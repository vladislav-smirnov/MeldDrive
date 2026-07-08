package io.github.airdaydreamers.melddrive.data.storage

import android.util.Log
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo0
import com.rapid7.client.dcerpc.transport.RPCTransport
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.EnumSet
import kotlin.time.Duration.Companion.milliseconds

@Suppress("TooManyFunctions")
class SmbFileSystemHandler(private val server: RemoteServer) : StorageSource {

    private val client = SMBClient()
    private var connection: Connection? = null
    private var session: Session? = null
    private val mutex = Mutex()
    private val semaphore = Semaphore(2) // Limit concurrent SMB operations to 2

    private suspend fun getSession(): Session = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentSession = session
            if (currentSession != null && connection?.isConnected == true) {
                return@withLock currentSession
            }

            // Close old ones if any
            runCatching { session?.close() }
            runCatching { connection?.close() }

            val conn = client.connect(server.host, server.port)
            val authContext = if (server.isAnonymous) {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(server.username ?: "", (server.password ?: "").toCharArray(), null)
            }
            val sess = conn.authenticate(authContext)

            connection = conn
            session = sess
            sess
        }
    }

    override suspend fun listFiles(path: String): List<FileItem> = withRetry { session ->
        withContext(Dispatchers.IO) {
            if (path.isEmpty()) {
                val transport: RPCTransport = SMBTransportFactories.SRVSVC.getTransport(session)
                val serverService = ServerService(transport)
                val shares: List<NetShareInfo0> = serverService.shares0
                shares.filter {
                    !it.netName.endsWith("$") && it.netName != "IPC$"
                }.map { share ->
                    FileItem(
                        path = share.netName,
                        name = share.netName,
                        isDirectory = true,
                        storageType = StorageType.SMB,
                    )
                }
            } else {
                val parts = path.split("/", limit = 2)
                val shareName = parts[0]
                val relativePath = if (parts.size > 1) parts[1] else ""

                (session.connectShare(shareName) as DiskShare).use { share ->
                    share.list(relativePath).map { info ->
                        val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                        FileItem(
                            path = "$shareName/${if (relativePath.isEmpty()) "" else "$relativePath/"}${info.fileName}",
                            name = info.fileName,
                            isDirectory = isDir,
                            size = if (isDir) 0 else info.endOfFile,
                            lastModified = info.changeTime.toEpochMillis(),
                            storageType = StorageType.SMB,
                        )
                    }.filter { it.name != "." && it.name != ".." }
                }
            }
        }
    }

    override suspend fun deleteFile(path: String): Boolean = withRetry { session ->
        withContext(Dispatchers.IO) {
            val parts = path.split("/", limit = 2)
            if (parts.size < 2) return@withContext false
            val shareName = parts[0]
            val relativePath = parts[1]

            (session.connectShare(shareName) as DiskShare).use { share ->
                if (share.folderExists(relativePath)) {
                    share.rmdir(relativePath, true)
                    true
                } else if (share.fileExists(relativePath)) {
                    share.rm(relativePath)
                    true
                } else {
                    false
                }
            }
        }
    }

    override suspend fun renameFile(path: String, newName: String): Boolean = withRetry { session ->
        withContext(Dispatchers.IO) {
            val parts = path.split("/", limit = 2)
            if (parts.size < 2) return@withContext false
            val shareName = parts[0]
            val relativePath = parts[1]

            val parentPath = relativePath.substringBeforeLast("/", "")
            val targetPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"

            (session.connectShare(shareName) as DiskShare).use { share ->
                share.open(
                    relativePath,
                    EnumSet.of(com.hierynomus.msdtyp.AccessMask.GENERIC_ALL),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                ).use { entry ->
                    entry.rename(targetPath)
                }
                true
            }
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Boolean = withRetry { session ->
        withContext(Dispatchers.IO) {
            val parts = parentPath.split("/", limit = 2)
            if (parts.isEmpty()) return@withContext false
            val shareName = parts[0]
            val relativePath = if (parts.size > 1) parts[1] else ""

            val newFolderPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

            (session.connectShare(shareName) as DiskShare).use { share ->
                share.mkdir(newFolderPath)
                true
            }
        }
    }

    override suspend fun getFileSize(path: String): Long = withRetry { session ->
        withContext(Dispatchers.IO) {
            val parts = path.split("/", limit = 2)
            if (parts.size < 2) return@withContext 0L
            val shareName = parts[0]
            val relativePath = parts[1]

            (session.connectShare(shareName) as DiskShare).use { share ->
                share.getFileInformation(relativePath).standardInformation.endOfFile
            }
        }
    }

    override suspend fun readFile(path: String, offset: Long, length: Int): ByteArray = withRetry { session ->
        withContext(Dispatchers.IO) {
            val parts = path.split("/", limit = 2)
            if (parts.size < 2) return@withContext ByteArray(0)
            val shareName = parts[0]
            val relativePath = parts[1]

            (session.connectShare(shareName) as DiskShare).use { share ->
                share.openFile(
                    relativePath,
                    EnumSet.of(com.hierynomus.msdtyp.AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                ).use { file ->
                    val buffer = ByteArray(length)
                    var totalBytesRead = 0
                    while (totalBytesRead < length) {
                        val bytesRead = file.read(buffer, offset + totalBytesRead, totalBytesRead, length - totalBytesRead)
                        if (bytesRead <= 0) break
                        totalBytesRead += bytesRead
                    }

                    if (totalBytesRead <= 0) return@withContext ByteArray(0)
                    if (totalBytesRead == length) {
                        buffer
                    } else {
                        buffer.copyOf(totalBytesRead)
                    }
                }
            }
        }
    }

    override suspend fun searchFiles(path: String, query: String): List<FileItem> = withRetry { session ->
        withContext(Dispatchers.IO) {
            val result = mutableListOf<FileItem>()
            if (path.isEmpty()) {
                val transport: RPCTransport = SMBTransportFactories.SRVSVC.getTransport(session)
                val serverService = ServerService(transport)
                val shares: List<NetShareInfo0> = serverService.shares0
                shares.filter {
                    !it.netName.endsWith("$") && it.netName != "IPC$" && it.netName.contains(query, ignoreCase = true)
                }.forEach { share ->
                    result.add(
                        FileItem(
                            path = share.netName,
                            name = share.netName,
                            isDirectory = true,
                            storageType = StorageType.SMB,
                        ),
                    )
                }
            } else {
                val parts = path.split("/", limit = 2)
                val shareName = parts[0]
                val relativePath = if (parts.size > 1) parts[1] else ""

                (session.connectShare(shareName) as DiskShare).use { share ->
                    recursiveSearch(share, shareName, relativePath, query, result)
                }
            }
            result
        }
    }

    private fun recursiveSearch(share: DiskShare, shareName: String, relativePath: String, query: String, result: MutableList<FileItem>) {
        share.list(relativePath).forEach { info ->
            if (info.fileName == "." || info.fileName == "..") return@forEach

            val currentRelativePath = if (relativePath.isEmpty()) info.fileName else "$relativePath/${info.fileName}"
            if (info.fileName.contains(query, ignoreCase = true)) {
                val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                result.add(
                    FileItem(
                        path = "$shareName/$currentRelativePath",
                        name = info.fileName,
                        isDirectory = isDir,
                        size = if (isDir) 0 else info.endOfFile,
                        lastModified = info.changeTime.toEpochMillis(),
                        storageType = StorageType.SMB,
                    ),
                )
            }

            val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            if (isDir) {
                recursiveSearch(share, shareName, currentRelativePath, query, result)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> withRetry(block: suspend (Session) -> T): T = withContext(Dispatchers.IO) {
        semaphore.withPermit {
            var lastException: Exception? = null
            repeat(MAX_RETRIES) { attempt ->
                try {
                    return@withContext block(getSession())
                } catch (e: IOException) {
                    handleRetryException(e, attempt)
                    lastException = e
                } catch (e: RuntimeException) {
                    handleRetryException(e, attempt)
                    lastException = e
                }
            }
            throw lastException ?: IOException("Unknown SMB error")
        }
    }

    private suspend fun handleRetryException(e: Exception, attempt: Int) {
        Log.e("SmbFileSystemHandler", "SMB operation failed (attempt ${attempt + 1}): ${e.message}")
        // Clear session to force reconnect on next attempt
        mutex.withLock {
            session = null
            connection = null
        }
        // Small delay before retry
        delay(RETRY_DELAY_MS.milliseconds)
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 100
    }
}
