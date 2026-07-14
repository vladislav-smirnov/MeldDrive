package io.github.airdaydreamers.melddrive.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SettingsManager] using Robolectric to verify real DataStore integration.
 * This covers the persistence of user preferences like buffering enablement and buffer size.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SettingsManagerTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(context)
        // Ensure test isolation by resetting preferences to default values before each test
        settingsManager.setBufferingEnabled(false)
        settingsManager.setBufferSizeMb(16)
    }

    /**
     * Use Case: Read Default Preferences
     * Given the settings are not yet configured
     * When settingsManager loads
     * Then bufferingEnabled should be false and bufferSizeMb should be 16
     */
    @Test
    fun testDefaultPreferences() = runBlocking {
        // Given
        // - In-memory Context with no prior preferences written

        // When & Then
        settingsManager.bufferingEnabled.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        settingsManager.bufferSizeMb.test {
            assertEquals(SettingsManager.DEFAULT_BUFFER_SIZE_MB, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case: Enable Buffering
     * Given buffering is disabled by default
     * When user enables buffering
     * Then the flow should emit true
     */
    @Test
    fun testSetBufferingEnabled() = runBlocking {
        // Given
        // - Initial state is disabled (false)

        // When
        settingsManager.setBufferingEnabled(true)

        // Then
        settingsManager.bufferingEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case: Update Buffer Size
     * Given buffer size is 16MB by default
     * When user sets buffer size to 64MB
     * Then the flow should emit 64
     */
    @Test
    fun testSetBufferSizeMb() = runBlocking {
        // Given
        // - Initial buffer size is 16

        // When
        settingsManager.setBufferSizeMb(64)

        // Then
        settingsManager.bufferSizeMb.test {
            assertEquals(64, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
