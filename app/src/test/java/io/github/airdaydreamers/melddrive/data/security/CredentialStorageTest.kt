package io.github.airdaydreamers.melddrive.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [CredentialStorage] using Robolectric.
 * Verifies saving, retrieving, and removing encrypted SMB server credentials.
 * Isolates the cryptographic logic by mocking [SecurityManager].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CredentialStorageTest {

    private lateinit var context: Context
    private lateinit var securityManager: SecurityManager
    private lateinit var credentialStorage: CredentialStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Ensure test isolation by cleaning up datastore directory before each test
        val datastoreDir = File(context.filesDir, "datastore")
        if (datastoreDir.exists()) {
            datastoreDir.deleteRecursively()
        }

        securityManager = mockk()

        // Mock securityManager mapping for clean, non-hardware-backed testing
        every { securityManager.encrypt(any()) } answers { "encrypted_" + firstArg<String>() }
        every { securityManager.decrypt(any()) } answers { firstArg<String>().removePrefix("encrypted_") }

        credentialStorage = CredentialStorage(context, securityManager)
    }

    /**
     * Use Case: Save and Retrieve Server Credentials
     * Given a remote server ID and its raw credentials (username and password)
     * When saveCredentials is called
     * Then retrieving username and password returns decrypted original credentials
     */
    @Test
    fun testSaveAndRetrieveCredentials() = runBlocking {
        // Given
        val serverId = 123L
        val username = "testUser"
        val password = "testPassword"

        // When
        credentialStorage.saveCredentials(serverId, username, password)

        // Then
        val retrievedUser = credentialStorage.getUsername(serverId)
        val retrievedPass = credentialStorage.getPassword(serverId)

        assertEquals(username, retrievedUser)
        assertEquals(password, retrievedPass)
    }

    /**
     * Use Case: Remove Server Credentials
     * Given a remote server ID with previously saved credentials
     * When removeCredentials is called
     * Then retrieving username and password returns null
     */
    @Test
    fun testRemoveCredentials() = runBlocking {
        // Given
        val serverId = 456L
        val username = "anotherUser"
        val password = "anotherPassword"
        credentialStorage.saveCredentials(serverId, username, password)

        // When
        credentialStorage.removeCredentials(serverId)

        // Then
        val retrievedUser = credentialStorage.getUsername(serverId)
        val retrievedPass = credentialStorage.getPassword(serverId)

        assertNull(retrievedUser)
        assertNull(retrievedPass)
    }

    /**
     * Use Case: Save Only Username or Only Password
     * Given a remote server ID with only a username and no password (or vice-versa)
     * When saveCredentials is called
     * Then retrieving credentials correctly returns the value for username and null for password
     */
    @Test
    fun testPartialCredentials() = runBlocking {
        // Given
        val serverId = 789L
        val username = "partialUser"

        // When
        credentialStorage.saveCredentials(serverId, username, null)

        // Then
        val retrievedUser = credentialStorage.getUsername(serverId)
        val retrievedPass = credentialStorage.getPassword(serverId)

        assertEquals(username, retrievedUser)
        assertNull(retrievedPass)
    }
}
