package io.github.airdaydreamers.melddrive.data.repository

import app.cash.turbine.test
import io.github.airdaydreamers.melddrive.data.discovery.DiscoveredServer
import io.github.airdaydreamers.melddrive.data.discovery.NetBiosDiscoveryManager
import io.github.airdaydreamers.melddrive.data.discovery.NsdDiscoveryManager
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ServerDiscoveryRepository] verifying merging of mDNS and NetBIOS discovery streams,
 * deduplication logic, and result emission.
 */
class ServerDiscoveryRepositoryTest {

    private lateinit var nsdDiscoveryManager: NsdDiscoveryManager
    private lateinit var netBiosDiscoveryManager: NetBiosDiscoveryManager
    private lateinit var repository: ServerDiscoveryRepository

    @BeforeEach
    fun setUp() {
        nsdDiscoveryManager = mockk()
        netBiosDiscoveryManager = mockk()
        repository = ServerDiscoveryRepository(nsdDiscoveryManager, netBiosDiscoveryManager)
    }

    /**
     * Use Case: Server Discovery Merging & Deduplication
     * Given NSD and NetBIOS managers discover servers with unique and duplicate hosts
     * When discoverServers is called
     * Then the flow should accumulate servers and deduplicate entries with matching (host, type)
     */
    @Test
    fun testDiscoverServersMergesAndDeduplicates() = runTest {
        // Given
        val nsdServer1 = DiscoveredServer("NAS-1", "192.168.1.10", 445, StorageType.SMB)
        val nsdServer2 = DiscoveredServer("WebDAV Server", "192.168.1.20", 80, StorageType.WEBDAV)
        val duplicateNetBiosServer = DiscoveredServer("NAS-1-NB", "192.168.1.10", 445, StorageType.SMB)
        val uniqueNetBiosServer = DiscoveredServer("Legacy-NAS", "192.168.1.30", 445, StorageType.SMB)

        every { nsdDiscoveryManager.discoverServices() } returns flowOf(nsdServer1, nsdServer2)
        every { netBiosDiscoveryManager.discoverServices() } returns flowOf(duplicateNetBiosServer, uniqueNetBiosServer)

        // When & Then
        repository.discoverServers().test {
            // Initial empty list emission from scan
            assertEquals(emptyList<DiscoveredServer>(), awaitItem())

            // Accumulated items
            awaitItem()
            awaitItem()
            awaitItem()
            val finalItem = awaitItem()

            assertEquals(3, finalItem.size)
            assertEquals("192.168.1.10", finalItem[0].host)
            assertEquals("192.168.1.20", finalItem[1].host)
            assertEquals("192.168.1.30", finalItem[2].host)

            awaitComplete()
        }
    }
}
