package io.github.airdaydreamers.melddrive.data.storage

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SmbFileSystemHandler] isolating network interactions
 * by mocking [SMBClient], [Connection], [Session], and [DiskShare].
 */
class SmbFileSystemHandlerTest {

    private lateinit var mockClient: SMBClient
    private lateinit var mockConnection: Connection
    private lateinit var mockSession: Session
    private lateinit var mockDiskShare: DiskShare
    private lateinit var server: RemoteServer
    private lateinit var handler: SmbFileSystemHandler

    @BeforeEach
    fun setUp() {
        mockClient = mockk(relaxed = true)
        mockConnection = mockk(relaxed = true)
        mockSession = mockk(relaxed = true)
        mockDiskShare = mockk(relaxed = true)

        server = RemoteServer(
            id = 1L,
            displayName = "Test Server",
            host = "192.168.1.10",
            port = 445,
            username = "user",
            password = "password",
            isAnonymous = false,
        )

        // Wire mock client connects and authenticates
        every { mockClient.connect(any<String>(), any<Int>()) } returns mockConnection
        every { mockConnection.authenticate(any()) } returns mockSession
        every { mockSession.connectShare(any()) } returns mockDiskShare

        handler = SmbFileSystemHandler(server, mockClient)
    }

    /**
     * Use Case: List Files inside an SMB Share
     * Given an SMB directory path "myShare/subfolder" containing 1 directory and 1 file
     * When listFiles is called
     * Then it should return the mapped FileItem instances correctly
     */
    @Test
    fun testListFilesInShare() = runBlocking {
        // Given
        val mockTime = mockk<com.hierynomus.msdtyp.FileTime> {
            every { toEpochMillis() } returns 12345678L
        }

        val mockFileInfoDir = mockk<FileIdBothDirectoryInformation> {
            every { fileName } returns "docs"
            every { fileAttributes } returns FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value
            every { changeTime } returns mockTime
        }
        val mockFileInfoFile = mockk<FileIdBothDirectoryInformation> {
            every { fileName } returns "photo.jpg"
            every { fileAttributes } returns 0L
            every { endOfFile } returns 1024L
            every { changeTime } returns mockTime
        }

        every { mockDiskShare.list("subfolder") } returns listOf(mockFileInfoDir, mockFileInfoFile)

        // When
        val result = handler.listFiles("myShare/subfolder")

        // Then
        assertEquals(2, result.size)

        val dirItem = result.first { it.name == "docs" }
        assertTrue(dirItem.isDirectory)
        assertEquals("myShare/subfolder/docs", dirItem.path)
        assertEquals(StorageType.SMB, dirItem.storageType)

        val fileItem = result.first { it.name == "photo.jpg" }
        assertEquals(1024L, fileItem.size)
        assertEquals("myShare/subfolder/photo.jpg", fileItem.path)
    }

    /**
     * Use Case: Delete File from SMB Share
     * Given an SMB path pointing to an existing file
     * When deleteFile is called
     * Then the file is deleted from the disk share
     */
    @Test
    fun testDeleteFile() = runBlocking {
        // Given
        val path = "myShare/subfolder/test.txt"
        every { mockDiskShare.folderExists("subfolder/test.txt") } returns false
        every { mockDiskShare.fileExists("subfolder/test.txt") } returns true

        // When
        val deleted = handler.deleteFile(path)

        // Then
        assertTrue(deleted)
        verify { mockDiskShare.rm("subfolder/test.txt") }
    }

    /**
     * Use Case: Create Folder in SMB Share
     * Given a parent path and a folder name
     * When createFolder is called
     * Then mkdir is invoked on the disk share with the target path
     */
    @Test
    fun testCreateFolder() = runBlocking {
        // Given
        val parentPath = "myShare/subfolder"
        val folderName = "newDirectory"

        // When
        val created = handler.createFolder(parentPath, folderName)

        // Then
        assertTrue(created)
        verify { mockDiskShare.mkdir("subfolder/newDirectory") }
    }
}
