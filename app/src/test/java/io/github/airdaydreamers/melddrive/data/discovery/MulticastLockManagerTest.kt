package io.github.airdaydreamers.melddrive.data.discovery

import android.content.Context
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MulticastLockManager] verifying reference counting and MulticastLock acquisition / release lifecycle.
 */
class MulticastLockManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockWifiManager: WifiManager
    private lateinit var mockMulticastLock: WifiManager.MulticastLock
    private lateinit var lockManager: MulticastLockManager

    @BeforeEach
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockWifiManager = mockk(relaxed = true)
        mockMulticastLock = mockk(relaxed = true)

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSystemService(Context.WIFI_SERVICE) } returns mockWifiManager
        every { mockWifiManager.createMulticastLock(any()) } returns mockMulticastLock
        every { mockMulticastLock.isHeld } returns false

        lockManager = MulticastLockManager(mockContext)
    }

    /**
     * Use Case: Acquire Multicast Lock
     * Given WifiManager and MulticastLock are available
     * When acquireLock is called
     * Then WifiManager should create and acquire the lock
     */
    @Test
    fun testAcquireLock() {
        // When
        lockManager.acquireLock("TestTag")

        // Then
        verify(exactly = 1) { mockWifiManager.createMulticastLock("TestTag") }
        verify(exactly = 1) { mockMulticastLock.acquire() }
    }

    /**
     * Use Case: Reference Counted Release
     * Given lock is acquired twice and released twice
     * When releaseLock is called
     * Then the physical lock should only release when reference count reaches 0
     */
    @Test
    fun testReferenceCountedRelease() {
        // Given
        var held = false
        every { mockMulticastLock.isHeld } answers { held }
        every { mockMulticastLock.acquire() } answers { held = true }
        every { mockMulticastLock.release() } answers { held = false }

        // When
        lockManager.acquireLock("TestTag") // count = 1, acquired
        lockManager.acquireLock("TestTag") // count = 2

        lockManager.releaseLock() // count = 1, still held
        verify(exactly = 0) { mockMulticastLock.release() }

        lockManager.releaseLock() // count = 0, released
        verify(exactly = 1) { mockMulticastLock.release() }
    }
}
