package io.github.airdaydreamers.melddrive.data.repository

import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ServerRepository] verifying that server registration
 * and deletion correctly interact with [RemoteServerDao] and [CredentialStorage].
 */
class ServerRepositoryTest {

    private lateinit var remoteServerDao: RemoteServerDao
    private lateinit var credentialStorage: CredentialStorage
    private lateinit var repository: ServerRepository

    @BeforeEach
    fun setUp() {
        remoteServerDao = mockk(relaxed = true)
        credentialStorage = mockk(relaxed = true)
        repository = ServerRepository(remoteServerDao, credentialStorage)
    }

    /**
     * Use Case: Enumerate Configured Remote Servers
     * Given a flow of servers inside the database
     * When getRemoteServers is called
     * Then it should return the exact flow of servers from the DAO
     */
    @Test
    fun testGetRemoteServers() {
        // Given
        val serversList = listOf(
            RemoteServer(id = 1L, displayName = "Server A", host = "1.1.1.1"),
            RemoteServer(id = 2L, displayName = "Server B", host = "2.2.2.2"),
        )
        every { remoteServerDao.getAllServers() } returns flowOf(serversList)

        // When
        val resultFlow = repository.getRemoteServers()

        // Then
        runBlocking {
            resultFlow.collect { list ->
                assertEquals(serversList, list)
            }
        }
    }

    /**
     * Use Case: Add Remote Server and Encrypt Credentials
     * Given a new RemoteServer instance and its plain text password
     * When addRemoteServer is called
     * Then it should insert the server in the database and save its credentials in CredentialStorage
     */
    @Test
    fun testAddRemoteServer() = runBlocking {
        // Given
        val server = RemoteServer(displayName = "Office NAS", host = "nas.local", username = "nasUser")
        val password = "nasPassword"
        coEvery { remoteServerDao.insertServer(server) } returns 42L

        // When
        repository.addRemoteServer(server, password)

        // Then
        coVerify { remoteServerDao.insertServer(server) }
        coVerify { credentialStorage.saveCredentials(42L, "nasUser", password) }
    }

    /**
     * Use Case: Delete Remote Server and Wipe Credentials
     * Given a RemoteServer instance
     * When deleteRemoteServer is called
     * Then it should delete the server from the database and wipe its credentials from CredentialStorage
     */
    @Test
    fun testDeleteRemoteServer() = runBlocking {
        // Given
        val server = RemoteServer(id = 88L, displayName = "Office NAS", host = "nas.local")

        // When
        repository.deleteRemoteServer(server)

        // Then
        coVerify { remoteServerDao.deleteServer(server) }
        coVerify { credentialStorage.removeCredentials(88L) }
    }
}
