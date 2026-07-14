package io.github.airdaydreamers.melddrive.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Integration/Unit tests for Room [AppDatabase] and [RemoteServerDao] using Robolectric.
 * Verifies insertion, retrieval, flow emissions, and deletion of remote servers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RemoteServerDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RemoteServerDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.remoteServerDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    /**
     * Use Case: Insert Server metadata and Retrieve by ID
     * Given a new RemoteServer instance
     * When insertServer is called followed by getServerById
     * Then the retrieved server should match the inserted server exactly
     */
    @Test
    fun testInsertAndGetServerById() {
        // Given
        val server = RemoteServer(
            displayName = "My SMB Server",
            host = "192.168.1.50",
            port = 445,
            username = "admin",
            password = null,
            isAnonymous = false,
        )

        // When
        val id = dao.insertServer(server)
        val retrieved = dao.getServerById(id)

        // Then
        assertNotNull(retrieved)
        assertEquals(id, retrieved!!.id)
        assertEquals("My SMB Server", retrieved.displayName)
        assertEquals("192.168.1.50", retrieved.host)
        assertEquals(445, retrieved.port)
        assertEquals("admin", retrieved.username)
        assertEquals(false, retrieved.isAnonymous)
    }

    /**
     * Use Case: Get All Servers as a Flow
     * Given two inserted RemoteServers
     * When getAllServers is observed
     * Then it should emit a list containing both servers
     */
    @Test
    fun testGetAllServersFlow() = runBlocking {
        // Given
        val server1 = RemoteServer(displayName = "Server 1", host = "host1")
        val server2 = RemoteServer(displayName = "Server 2", host = "host2")

        // When & Then
        dao.getAllServers().test {
            // First emission is empty list
            assertTrue(awaitItem().isEmpty())

            dao.insertServer(server1)
            assertEquals(1, awaitItem().size)

            dao.insertServer(server2)
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.any { it.displayName == "Server 1" })
            assertTrue(items.any { it.displayName == "Server 2" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case: Delete Server metadata
     * Given an inserted RemoteServer
     * When deleteServer is called
     * Then getServerById should return null and the Flow should emit an empty list
     */
    @Test
    fun testDeleteServer() = runBlocking {
        // Given
        val server = RemoteServer(displayName = "To Delete", host = "delete-me")
        val id = dao.insertServer(server)
        val savedServer = dao.getServerById(id)
        assertNotNull(savedServer)

        // When
        dao.deleteServer(savedServer!!)

        // Then
        val retrieved = dao.getServerById(id)
        assertNull(retrieved)

        dao.getAllServers().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
