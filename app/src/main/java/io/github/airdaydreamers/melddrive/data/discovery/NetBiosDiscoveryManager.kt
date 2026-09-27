package io.github.airdaydreamers.melddrive.data.discovery

import io.github.airdaydreamers.melddrive.data.model.StorageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetBiosDiscoveryManager @Inject constructor(private val multicastLockManager: MulticastLockManager) {
    companion object {
        private const val NETBIOS_PORT = 137
        private const val SOCKET_TIMEOUT_MS = 2000
        private const val BUFFER_SIZE = 1024
        private const val DEFAULT_SMB_PORT = 445

        private const val MIN_RESPONSE_LENGTH = 57
        private const val NUM_NAMES_OFFSET = 56
        private const val NAMES_START_OFFSET = 57
        private const val NAME_ENTRY_SIZE = 18
        private const val NAME_STRING_LENGTH = 15
        private const val TYPE_OFFSET_IN_ENTRY = 15
        private const val NETBIOS_TYPE_FILE_SERVER = 0x20
        private const val NETBIOS_TYPE_WORKSTATION = 0x00
        private const val BYTE_MASK = 0xFF

        // NetBIOS Node Status Request Query for wildcard "*"
        // Transaction ID: 0x1234, Flags: 0x0000 (Query), Questions: 1
        private val NETBIOS_NAME_QUERY_PACKET = byteArrayOf(
            0x12.toByte(), 0x34.toByte(), // Transaction ID
            0x00.toByte(), 0x00.toByte(), // Flags: Query
            0x00.toByte(), 0x01.toByte(), // Questions: 1
            0x00.toByte(), 0x00.toByte(), // Answer RRs: 0
            0x00.toByte(), 0x00.toByte(), // Authority RRs: 0
            0x00.toByte(), 0x00.toByte(), // Additional RRs: 0
            // Encoded Name: '*' padded with 15 spaces (CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA)
            0x20.toByte(),
            0x43.toByte(), 0x4B.toByte(), 0x41.toByte(), 0x41.toByte(),
            0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
            0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
            0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
            0x41.toByte(), 0x41.toByte(), 0x41.toByte(), 0x41.toByte(),
            0x41.toByte(), 0x41.toByte(),
            0x00.toByte(), // Null terminator
            0x00.toByte(), 0x21.toByte(), // Type: NBSTAT (Node Status)
            0x00.toByte(), 0x01.toByte(), // Class: IN
        )
    }

    fun discoverServices(): Flow<DiscoveredServer> = callbackFlow {
        multicastLockManager.acquireLock("NetBiosDiscovery")

        var socket: DatagramSocket? = null
        try {
            socket = createDatagramSocket()
            val queryPacket = createQueryPacket()

            withContext(Dispatchers.IO) {
                sendQueryPacket(socket, queryPacket)
                receivePacketsLoop(socket) { server ->
                    trySend(server)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing NetBIOS DatagramSocket")
        } finally {
            socket?.close()
        }

        awaitClose {
            socket?.close()
            multicastLockManager.releaseLock()
        }
    }

    private fun createDatagramSocket(): DatagramSocket = DatagramSocket().apply {
        broadcast = true
        soTimeout = SOCKET_TIMEOUT_MS
    }

    private fun createQueryPacket(): DatagramPacket {
        val broadcastAddress = InetAddress.getByName("255.255.255.255")
        return DatagramPacket(
            NETBIOS_NAME_QUERY_PACKET,
            NETBIOS_NAME_QUERY_PACKET.size,
            broadcastAddress,
            NETBIOS_PORT,
        )
    }

    private fun sendQueryPacket(socket: DatagramSocket, sendPacket: DatagramPacket) {
        try {
            socket.send(sendPacket)
            Timber.d("NetBIOS query broadcast packet sent")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send NetBIOS query packet")
        }
    }

    private fun CoroutineScope.receivePacketsLoop(socket: DatagramSocket, onServerDiscovered: (DiscoveredServer) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        var shouldContinue = true
        while (isActive && shouldContinue) {
            shouldContinue = receiveSinglePacket(socket, buffer, onServerDiscovered)
        }
    }

    private fun CoroutineScope.receiveSinglePacket(socket: DatagramSocket, buffer: ByteArray, onServerDiscovered: (DiscoveredServer) -> Unit): Boolean = try {
        val receivePacket = DatagramPacket(buffer, buffer.size)
        socket.receive(receivePacket)

        val hostAddress = receivePacket.address?.hostAddress
        if (hostAddress != null) {
            val hostName = parseNetBiosName(receivePacket.data, receivePacket.length) ?: hostAddress

            Timber.d("NetBIOS SMB server discovered: %s @ %s", hostName, hostAddress)

            val server = DiscoveredServer(
                name = hostName,
                host = hostAddress,
                port = DEFAULT_SMB_PORT,
                type = StorageType.SMB,
            )
            onServerDiscovered(server)
        }
        true
    } catch (e: SocketTimeoutException) {
        // Expected timeout during periodic listen loops
        Timber.d(e, "Expected timeout during periodic listen loops")
        false
    } catch (e: Exception) {
        if (isActive) {
            Timber.w(e, "Error receiving NetBIOS packet")
        }
        false
    }

    private fun parseNetBiosName(data: ByteArray, length: Int): String? {
        if (length < MIN_RESPONSE_LENGTH) return null
        try {
            val numNames = data[NUM_NAMES_OFFSET].toInt() and BYTE_MASK
            if (numNames <= 0) return null

            var offset = NAMES_START_OFFSET
            repeat(numNames) {
                if (offset + NAME_ENTRY_SIZE > length) return@repeat
                val nameBytes = data.copyOfRange(offset, offset + NAME_STRING_LENGTH)
                val rawName = String(nameBytes, Charsets.US_ASCII).trim()
                val type = data[offset + TYPE_OFFSET_IN_ENTRY].toInt() and BYTE_MASK

                // Type 0x20 indicates File Server service, 0x00 Workstation/Domain
                if (type == NETBIOS_TYPE_FILE_SERVER || type == NETBIOS_TYPE_WORKSTATION) {
                    if (rawName.isNotBlank()) return rawName
                }
                offset += NAME_ENTRY_SIZE
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse NetBIOS response packet")
        }
        return null
    }
}
