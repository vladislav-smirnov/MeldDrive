package io.github.airdaydreamers.melddrive.ui.viewmodel

import android.os.Environment
import app.cash.turbine.test
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.repository.ServerRepository
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerIntent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for [FileManagerViewModel] using Turbine and MockK.
 * Tests sidebar initialization, directory navigation, query-length dependent search,
 * and deleting of viewed servers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileManagerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fileRepository: FileRepository
    private lateinit var serverRepository: ServerRepository
    private lateinit var viewModel: FileManagerViewModel

    private val serversFlow = MutableStateFlow<List<RemoteServer>>(emptyList())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fileRepository = mockk(relaxed = true)
        serverRepository = mockk(relaxed = true)

        // Android SDK stubs contain null for DIRECTORY constants.
        // We set them via reflection to avoid NullPointerException in File(parent, child) constructor.
        try {
            val envClass = Environment::class.java
            envClass.getField("DIRECTORY_DOWNLOADS").set(null, "Downloads")
            envClass.getField("DIRECTORY_DCIM").set(null, "DCIM")
            envClass.getField("DIRECTORY_MOVIES").set(null, "Movies")
            envClass.getField("DIRECTORY_MUSIC").set(null, "Music")
        } catch (e: Exception) {
            System.err.println("Failed to set static Environment fields: ${e.message}")
        }

        // Mock Environment.getExternalStorageDirectory() static method
        mockkStatic(Environment::class)
        val mockFile = mockk<File> {
            every { absolutePath } returns "/mock/storage"
        }
        every { Environment.getExternalStorageDirectory() } returns mockFile

        every { serverRepository.getRemoteServers() } returns serversFlow

        // Initialize viewModel
        viewModel = FileManagerViewModel(fileRepository, serverRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Use Case: Load Sidebar and Default Directory on Start
     * Given the repository contains a remote server and local files
     * When the ViewModel initializes
     * Then sidebarItems should include both local system folders and the remote server, and local files should load
     */
    @Test
    fun testInitialization() = runBlocking {
        // Given
        val remoteServers = listOf(RemoteServer(id = 5L, displayName = "My NAS", host = "nas"))
        coEvery { fileRepository.listFiles("/mock/storage", StorageType.LOCAL, null) } returns listOf(
            FileItem("/mock/storage/doc.pdf", "doc.pdf", false),
        )

        // When
        serversFlow.value = remoteServers

        // Then
        viewModel.state.test {
            val state = awaitItem()
            // Check sidebar mapping
            assertTrue(state.sidebarItems.any { it.title == "My NAS" && it.serverId == 5L })
            assertTrue(state.sidebarItems.any { it.title == "Home" })

            // Check loaded files
            assertEquals("/mock/storage", state.currentPath)
            assertEquals(StorageType.LOCAL, state.currentStorageType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Use Case: Navigate to New Directory
     * Given the user is viewing local root
     * When user sends a NavigateTo intent to "SMB" storage with server ID 10
     * Then currentPath, storageType, and serverId should be updated, and files reloaded
     */
    @Test
    fun testNavigateToDirectory() = runBlocking {
        // When
        viewModel.onIntent(FileManagerIntent.NavigateTo("Photos", StorageType.SMB, 10L))

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("Photos", state.currentPath)
            assertEquals(StorageType.SMB, state.currentStorageType)
            assertEquals(10L, state.currentServerId)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { fileRepository.listFiles("Photos", StorageType.SMB, 10L) }
    }

    /**
     * Use Case: Search Depends on Query Length
     * Given the user is typing in search bar
     * When user searches for "ab" (short query < 3 characters)
     * Then search files should not be called, instead reloads directory
     * When user searches for "abc" (query length >= 3)
     * Then search files is triggered on the repository
     */
    @Test
    fun testSearchQueryLengthDependent() = runBlocking {
        // When: Search with short query "ab"
        viewModel.onIntent(FileManagerIntent.Search("ab"))
        coVerify(exactly = 0) { fileRepository.searchFiles(any(), any(), any(), any()) }

        // When: Search with query >= 3 chars "abc"
        viewModel.onIntent(FileManagerIntent.Search("abc"))
        coVerify(exactly = 1) { fileRepository.searchFiles(any(), "abc", any(), any()) }
    }

    /**
     * Use Case: Reset to Local on Active Server Deletion
     * Given user is currently viewing SMB server ID 12
     * When the server is deleted
     * Then the ViewModel should reset current storage to LOCAL and current path to local root
     */
    @Test
    fun testDeleteActiveRemoteServerResetsToLocal() = runBlocking {
        // Given - Active server is ID 12
        viewModel.onIntent(FileManagerIntent.NavigateTo("NAS/Share", StorageType.SMB, 12L))
        val targetServer = RemoteServer(id = 12L, displayName = "My NAS", host = "nas")
        every { serverRepository.getRemoteServers() } returns flowOf(listOf(targetServer))

        // When
        viewModel.onIntent(FileManagerIntent.DeleteRemoteServer(12L))

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(StorageType.LOCAL, state.currentStorageType)
            assertEquals("/mock/storage", state.currentPath)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
