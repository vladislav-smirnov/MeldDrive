package io.github.airdaydreamers.melddrive.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.airdaydreamers.melddrive.data.model.StorageType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsdDiscoveryManager @Inject constructor(@ApplicationContext private val context: Context, private val multicastLockManager: MulticastLockManager) {
    companion object {
        private const val DEFAULT_SMB_PORT = 445
        private const val DEFAULT_HTTP_PORT = 80

        val SERVICE_TYPES = listOf(
            "_smb._tcp." to StorageType.SMB,
            "_webdav._tcp." to StorageType.WEBDAV,
            "_http._tcp." to StorageType.WEBDAV,
        )
    }

    private class DiscoverySession {
        @Volatile
        var isCancelled: Boolean = false
    }

    private data class PendingResolve(
        val session: DiscoverySession,
        val serviceInfo: NsdServiceInfo,
        val storageType: StorageType,
        val onServerDiscovered: (DiscoveredServer) -> Unit,
    )

    private val pendingResolves = ArrayDeque<PendingResolve>()
    private var isResolving = false

    fun discoverServices(): Flow<DiscoveredServer> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            Timber.w("NsdManager is not available on this device")
            close()
            return@callbackFlow
        }

        val session = DiscoverySession()

        multicastLockManager.acquireLock("NsdDiscovery")

        val discoveryListeners = startDiscoveryForTypes(nsdManager, session) { discoveredServer ->
            trySend(discoveredServer)
        }

        awaitClose {
            stopDiscoveryListeners(nsdManager, discoveryListeners)
            cancelSession(session)
            multicastLockManager.releaseLock()
        }
    }

    private fun startDiscoveryForTypes(
        nsdManager: NsdManager,
        session: DiscoverySession,
        onServerDiscovered: (DiscoveredServer) -> Unit,
    ): List<Pair<String, NsdManager.DiscoveryListener>> {
        val discoveryListeners = mutableListOf<Pair<String, NsdManager.DiscoveryListener>>()
        SERVICE_TYPES.forEach { (serviceType, storageType) ->
            val listener = createDiscoveryListener(nsdManager, session, storageType, onServerDiscovered)
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                discoveryListeners.add(serviceType to listener)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start NSD discovery for %s", serviceType)
            }
        }
        return discoveryListeners
    }

    private fun createDiscoveryListener(
        nsdManager: NsdManager,
        session: DiscoverySession,
        storageType: StorageType,
        onServerDiscovered: (DiscoveredServer) -> Unit,
    ): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Timber.e("NSD Discovery start failed for type %s: error %d", serviceType, errorCode)
            safeStopServiceDiscovery(nsdManager, this)
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Timber.e("NSD Discovery stop failed for type %s: error %d", serviceType, errorCode)
        }

        override fun onDiscoveryStarted(registeredServiceType: String?) {
            Timber.d("NSD Discovery started for type %s", registeredServiceType)
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Timber.d("NSD Discovery stopped for type %s", serviceType)
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            if (serviceInfo == null || session.isCancelled) return
            Timber.d("NSD Service found: %s, type: %s", serviceInfo.serviceName, serviceInfo.serviceType)
            queueResolve(nsdManager, session, serviceInfo, storageType, onServerDiscovered)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            Timber.d("NSD Service lost: %s", serviceInfo?.serviceName)
        }
    }

    private fun queueResolve(
        nsdManager: NsdManager,
        session: DiscoverySession,
        serviceInfo: NsdServiceInfo,
        storageType: StorageType,
        onServerDiscovered: (DiscoveredServer) -> Unit,
    ) {
        synchronized(pendingResolves) {
            if (session.isCancelled) return
            pendingResolves.addLast(PendingResolve(session, serviceInfo, storageType, onServerDiscovered))
            processNextResolveLocked(nsdManager)
        }
    }

    private fun processNextResolveLocked(nsdManager: NsdManager) {
        if (isResolving || pendingResolves.isEmpty()) {
            return
        }
        val next = pendingResolves.removeFirst()
        if (next.session.isCancelled) {
            processNextResolveLocked(nsdManager)
            return
        }
        isResolving = true
        resolveNsdService(nsdManager, next)
    }

    private fun onResolveFinished(nsdManager: NsdManager) {
        synchronized(pendingResolves) {
            isResolving = false
            processNextResolveLocked(nsdManager)
        }
    }

    private fun cancelSession(session: DiscoverySession) {
        synchronized(pendingResolves) {
            session.isCancelled = true
            pendingResolves.removeAll { it.session == session }
        }
    }

    private fun resolveNsdService(nsdManager: NsdManager, pendingResolve: PendingResolve) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Timber.w("NSD Service resolve failed: %d for %s", errorCode, pendingResolve.serviceInfo.serviceName)
                onResolveFinished(nsdManager)
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                try {
                    if (resolvedInfo != null && !pendingResolve.session.isCancelled) {
                        val hostAddress = resolvedInfo.host?.hostAddress
                        if (hostAddress != null) {
                            val port = resolvedInfo.port
                            val serviceName = resolvedInfo.serviceName ?: hostAddress

                            Timber.d("NSD Service resolved: %s @ %s:%d", serviceName, hostAddress, port)

                            val discoveredServer = DiscoveredServer(
                                name = serviceName,
                                host = hostAddress,
                                port = if (port > 0) port else defaultPortFor(pendingResolve.storageType),
                                type = pendingResolve.storageType,
                            )
                            pendingResolve.onServerDiscovered(discoveredServer)
                        }
                    }
                } finally {
                    onResolveFinished(nsdManager)
                }
            }
        }

        try {
            nsdManager.resolveService(pendingResolve.serviceInfo, resolveListener)
        } catch (e: Exception) {
            Timber.e(e, "Exception resolving service %s", pendingResolve.serviceInfo.serviceName)
            onResolveFinished(nsdManager)
        }
    }

    private fun stopDiscoveryListeners(nsdManager: NsdManager, discoveryListeners: List<Pair<String, NsdManager.DiscoveryListener>>) {
        discoveryListeners.forEach { (_, listener) ->
            safeStopServiceDiscovery(nsdManager, listener)
        }
    }

    private fun safeStopServiceDiscovery(nsdManager: NsdManager, listener: NsdManager.DiscoveryListener) {
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Timber.w(e, "Error stopping service discovery")
        }
    }

    private fun defaultPortFor(storageType: StorageType): Int = when (storageType) {
        StorageType.SMB -> DEFAULT_SMB_PORT
        StorageType.WEBDAV -> DEFAULT_HTTP_PORT
        else -> DEFAULT_HTTP_PORT
    }
}
