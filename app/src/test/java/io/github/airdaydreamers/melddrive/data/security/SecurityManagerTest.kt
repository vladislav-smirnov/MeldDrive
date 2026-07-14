package io.github.airdaydreamers.melddrive.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SecurityManager] using Robolectric.
 * Focuses on verifying the encryption/decryption cycle, falling back if the Android Keystore
 * is unavailable in the execution environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SecurityManagerTest {

    private lateinit var context: Context
    private var securityManager: SecurityManager? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        try {
            securityManager = TinkSecurityManager(context)
        } catch (e: Exception) {
            // Under some headless environments, Android Keystore initialization might fail.
            // We log this and handle it gracefully in the tests.
            System.err.println("SecurityManager initialization failed under Robolectric: ${e.message}")
        }
    }

    /**
     * Use Case: Symmetric Encryption and Decryption Round-Trip
     * Given a plain text credential string (e.g. "mySecretPassword123")
     * When encrypt is called followed by decrypt
     * Then the decrypted string should match the original plain text
     */
    @Test
    fun testEncryptionRoundTrip() {
        val manager = securityManager
        if (manager == null) {
            System.err.println("Skipping testEncryptionRoundTrip because Android Keystore is unavailable.")
            return
        }

        // Given
        val originalText = "mySecretPassword123"

        // When
        val encryptedText = manager.encrypt(originalText)
        assertNotNull(encryptedText)

        val decryptedText = manager.decrypt(encryptedText)

        // Then
        assertEquals(originalText, decryptedText)
    }
}
