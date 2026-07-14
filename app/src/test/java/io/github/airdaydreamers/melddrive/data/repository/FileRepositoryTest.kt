package io.github.airdaydreamers.melddrive.data.repository

import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.storage.LocalFileSystemHandler
import io.github.airdaydreamers.melddrive.data.storage.SmbFileSystemHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [FileRepository] validating correct request routing
 * to appropriate file system handlers (LOCAL vs SMB) via dependency injection.
 */
class FileRepositoryTest {

    private lateinit var remoteServerDao: RemoteServerDao
    private lateinit var credentialStorage: CredentialStorage
    private lateinit var mockLocalHandler: LocalFileSystemHandler
    private lateinit var mockSmbHandler: SmbFileSystemHandler
    private lateinit var mockSmbHandlerFactory: SmbFileSystemHandler.Factory
    private lateinit var repository: FileRepository

    @BeforeEach
    fun setUp() {
        remoteServerDao = mockk(relaxed = true)
        credentialStorage = mockk(relaxed = true)
        mockLocalHandler = mockk(relaxed = true)
        mockSmbHandler = mockk(relaxed = true)
        mockSmbHandlerFactory = mockk(relaxed = true)

        every { mockSmbHandlerFactory.create(any()) } returns mockSmbHandler

        repository = FileRepository(
            remoteServerDao = remoteServerDao,
            credentialStorage = credentialStorage,
            localHandler = mockLocalHandler,
            smbHandlerFactory = mockSmbHandlerFactory,
        )
    }

    /**
     * Use Case: Route Directory Listing to Local Storage Handler
     * Given a local file path
     * When listFiles is called
     * Then it should delegate the listing to the injected LocalFileSystemHandler
     */
    @Test
    fun testListFilesLocalRouting() = runBlocking {
        // Given
        val path = "/some/local/path"
        val storageType = StorageType.LOCAL
        val mockFiles = listOf(FileItem(path, "file.txt", false))

        coEvery { mockLocalHandler.listFiles(path) } returns mockFiles

        // When
        val result = repository.listFiles(path, storageType, null)

        // Then
        assertEquals(mockFiles, result)
        coVerify { mockLocalHandler.listFiles(path) }
    }

    /**
     * Use Case: Route File Deletion to SMB Storage Handler
     * Given an SMB file path and server ID
     * When deleteFile is called
     * Then it should resolve the server and delegate the deletion to the SMB handler factory
     */
    @Test
    fun testDeleteFileSmbRouting() = runBlocking {
        // Given
        val path = "share/file.txt"
        val storageType = StorageType.SMB
        val serverId = 1L
        val mockServer = RemoteServer(id = serverId, displayName = "Test", host = "192.168.1.1")

        coEvery { remoteServerDao.getServerById(serverId) } returns mockServer
        coEvery { mockSmbHandler.deleteFile(path) } returns true

        // When
        repository.deleteFile(path, storageType, serverId)

        // Then
        coVerify { remoteServerDao.getServerById(serverId) }
        coVerify { mockSmbHandler.deleteFile(path) }
    }

    /**
     * Use Case: Route File Renaming to Local Storage Handler
     * Given a local file path and a new name
     * When renameFile is called
     * Then it should delegate the rename operation to the LocalFileSystemHandler
     */
    @Test
    fun testRenameFileLocalRouting() = runBlocking {
        // Given
        val path = "/path/old.txt"
        val newName = "new.txt"
        val storageType = StorageType.LOCAL

        coEvery { mockLocalHandler.renameFile(path, newName) } returns true

        // When
        repository.renameFile(path, newName, storageType, null)

        // Then
        coVerify { mockLocalHandler.renameFile(path, newName) }
    }
}
