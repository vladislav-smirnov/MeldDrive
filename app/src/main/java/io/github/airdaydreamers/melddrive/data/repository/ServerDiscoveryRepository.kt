package io.github.airdaydreamers.melddrive.data.repository

import io.github.airdaydreamers.melddrive.data.discovery.DiscoveredServer
import io.github.airdaydreamers.melddrive.data.discovery.NetBiosDiscoveryManager
import io.github.airdaydreamers.melddrive.data.discovery.NsdDiscoveryManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerDiscoveryRepository @Inject constructor(
    private val nsdDiscoveryManager: NsdDiscoveryManager,
    private val netBiosDiscoveryManager: NetBiosDiscoveryManager,
) {
    companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
    }

    fun discoverServers(timeoutMs: Long = SCAN_TIMEOUT_MS): Flow<List<DiscoveredServer>> = flow {
        withTimeoutOrNull(timeoutMs) {
            merge(
                nsdDiscoveryManager.discoverServices(),
                netBiosDiscoveryManager.discoverServices(),
            )
                .catch { e ->
                    Timber.e(e, "Error during server discovery flow")
                }
                .scan(emptyList<DiscoveredServer>()) { accumulated, server ->
                    val exists = accumulated.any {
                        it.host == server.host && it.type == server.type
                    }
                    if (!exists) {
                        accumulated + server
                    } else {
                        accumulated
                    }
                }
                .collect { list ->
                    emit(list)
                }
        }
    }
}
