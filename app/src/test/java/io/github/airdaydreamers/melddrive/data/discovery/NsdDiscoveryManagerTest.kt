package io.github.airdaydreamers.melddrive.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress

class NsdDiscoveryManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockNsdManager: NsdManager
    private lateinit var mockMulticastLockManager: MulticastLockManager
    private lateinit var nsdDiscoveryManager: NsdDiscoveryManager

    @BeforeEach
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockNsdManager = mockk(relaxed = true)
        mockMulticastLockManager = mockk(relaxed = true)

        every { mockContext.getSystemService(Context.NSD_SERVICE) } returns mockNsdManager

        nsdDiscoveryManager = NsdDiscoveryManager(mockContext, mockMulticastLockManager)
    }

    @Test
    fun testMulticastLockNotAcquiredWhenNsdManagerNull() = runTest {
        every { mockContext.getSystemService(Context.NSD_SERVICE) } returns null

        val result = nsdDiscoveryManager.discoverServices().toList()

        assertEquals(emptyList<DiscoveredServer>(), result)
        verify(exactly = 0) { mockMulticastLockManager.acquireLock(any()) }
        verify(exactly = 0) { mockMulticastLockManager.releaseLock() }
    }

    @Test
    fun testMulticastLockAcquiredWhenNsdManagerAvailable() = runTest {
        val job = launch {
            nsdDiscoveryManager.discoverServices().collect {}
        }
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { mockMulticastLockManager.acquireLock("NsdDiscovery") }

        job.cancel()
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { mockMulticastLockManager.releaseLock() }
    }

    @Suppress("DEPRECATION")
    @Test
    fun testSequentialResolveServicesQueue() = runTest {
        val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
        every {
            mockNsdManager.discoverServices(
                any<String>(),
                any<Int>(),
                capture(discoveryListeners),
            )
        } returns Unit

        val resolveListeners = mutableListOf<NsdManager.ResolveListener>()
        val resolveInfos = mutableListOf<NsdServiceInfo>()
        every {
            mockNsdManager.resolveService(
                capture(resolveInfos),
                capture(resolveListeners),
            )
        } returns Unit

        val discoveredServers = mutableListOf<DiscoveredServer>()
        val job = launch {
            nsdDiscoveryManager.discoverServices().collect { discoveredServers.add(it) }
        }
        testScheduler.advanceUntilIdle()

        // Simulate 2 services found at the same time
        val serviceInfo1 = mockk<NsdServiceInfo>(relaxed = true) {
            every { serviceName } returns "Server1"
            every { serviceType } returns "_smb._tcp."
        }
        val serviceInfo2 = mockk<NsdServiceInfo>(relaxed = true) {
            every { serviceName } returns "Server2"
            every { serviceType } returns "_smb._tcp."
        }

        discoveryListeners[0].onServiceFound(serviceInfo1)
        discoveryListeners[0].onServiceFound(serviceInfo2)

        // Only first service should be submitted to resolveService initially
        assertEquals(1, resolveInfos.size)
        assertEquals("Server1", resolveInfos[0].serviceName)

        // Simulate service1 resolved
        val mockInetAddress = mockk<InetAddress>(relaxed = true) {
            every { hostAddress } returns "192.168.1.50"
        }
        val resolvedInfo1 = mockk<NsdServiceInfo>(relaxed = true) {
            every { serviceName } returns "Server1"
            every { host } returns mockInetAddress
            every { port } returns 445
        }
        resolveListeners[0].onServiceResolved(resolvedInfo1)

        // Now second service should be popped from queue and resolveService called
        assertEquals(2, resolveInfos.size)
        assertEquals("Server2", resolveInfos[1].serviceName)

        job.cancel()
    }

    @Suppress("DEPRECATION")
    @Test
    fun testCancelAndRestartScanWithPendingResolve() = runTest {
        val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
        every {
            mockNsdManager.discoverServices(
                any<String>(),
                any<Int>(),
                capture(discoveryListeners),
            )
        } returns Unit

        val resolveListeners = mutableListOf<NsdManager.ResolveListener>()
        val resolveInfos = mutableListOf<NsdServiceInfo>()
        every {
            mockNsdManager.resolveService(
                capture(resolveInfos),
                capture(resolveListeners),
            )
        } returns Unit

        val scan1Discovered = mutableListOf<DiscoveredServer>()
        val job1 = launch {
            nsdDiscoveryManager.discoverServices().collect { scan1Discovered.add(it) }
        }
        testScheduler.advanceUntilIdle()

        // Scan 1 finds Service 1
        val serviceInfo1 = mockk<NsdServiceInfo>(relaxed = true) {
            every { serviceName } returns "Server1"
            every { serviceType } returns "_smb._tcp."
        }
        discoveryListeners[0].onServiceFound(serviceInfo1)

        // Service 1 is now resolving (resolveService called for Server1)
        assertEquals(1, resolveInfos.size)
        assertEquals("Server1", resolveInfos[0].serviceName)

        // Cancel Scan 1 while Service 1 is still resolving in NsdManager
        job1.cancel()
        testScheduler.advanceUntilIdle()

        // Start Scan 2
        val scan2Discovered = mutableListOf<DiscoveredServer>()
        val job2 = launch {
            nsdDiscoveryManager.discoverServices().collect { scan2Discovered.add(it) }
        }
        testScheduler.advanceUntilIdle()

        // Scan 2 finds Service 2
        val serviceInfo2 = mockk<NsdServiceInfo>(relaxed = true) {
            every { serviceName } returns "Server2"
            every { serviceType } returns "_smb._tcp."
        }
        val scan2ListenerIndex = discoveryListeners.lastIndex
        discoveryListeners[scan2ListenerIndex].onServiceFound(serviceInfo2)

        // Service 2 should NOT be sent to resolveService yet because Service 1's resolve is still in progress in NsdManager
        assertEquals(1, resolveInfos.size)

        // Now Service 1 resolve completes from NsdManager
        val mockInetAddress1 = mockk<InetAddress>(relaxed = true) {
            every { hostAddress } returns "192.168.1.50"
        }
        val resolvedInfo1 = mockk<NsdServiceInfo>(relaxed = true) {
            every { serviceName } returns "Server1"
            every { host } returns mockInetAddress1
            every { port } returns 445
        }
        resolveListeners[0].onServiceResolved(resolvedInfo1)

        // Scan 1 should NOT receive Server1 (since scan 1 was cancelled)
        assertEquals(0, scan1Discovered.size)

        // Now Service 2 from Scan 2 should be dequeued and resolveService called
        assertEquals(2, resolveInfos.size)
        assertEquals("Server2", resolveInfos[1].serviceName)

        job2.cancel()
    }
}
