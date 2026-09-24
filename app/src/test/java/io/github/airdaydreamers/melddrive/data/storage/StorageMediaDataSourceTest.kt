package io.github.airdaydreamers.melddrive.data.storage

import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [StorageMediaDataSource] verifying chunk caching,
 * seekable position reads, and EOF contracts for Stagefright/MediaExtractor.
 */
class StorageMediaDataSourceTest {

    private lateinit var repository: FileRepository
    private val testPath = "share/video.mp4"
    private val testStorageType = StorageType.SMB
    private val testServerId = 1L
    private val testFileSize = 10000L

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
    }

    /**
     * Use Case: Return total size of remote media file
     * Given initialized StorageMediaDataSource
     * When getSize is called
     * Then it should return the exact file size
     */
    @Test
    fun testGetSize() {
        val dataSource = StorageMediaDataSource(
            repository = repository,
            path = testPath,
            storageType = testStorageType,
            serverId = testServerId,
            size = testFileSize,
        )

        assertEquals(testFileSize, dataSource.size)
    }

    /**
     * Use Case: Read header bytes via chunk prefetch buffer
     * Given a requested position and buffer size
     * When readAt is called
     * Then it should fetch the chunk from repository and copy requested bytes to target buffer
     */
    @Test
    fun testReadAtWithChunkCache() = runBlocking {
        // Given
        val fakeChunk = ByteArray(1024) { (it % 100).toByte() }
        coEvery {
            repository.readFile(testPath, 0L, any(), testStorageType, testServerId)
        } returns fakeChunk

        val dataSource = StorageMediaDataSource(
            repository = repository,
            path = testPath,
            storageType = testStorageType,
            serverId = testServerId,
            size = testFileSize,
        )

        val targetBuffer = ByteArray(64)

        // When
        val bytesRead = dataSource.readAt(0L, targetBuffer, 0, 64)

        // Then
        assertEquals(64, bytesRead)
        assertEquals(fakeChunk[0], targetBuffer[0])
        assertEquals(fakeChunk[63], targetBuffer[63])
        coVerify(exactly = 1) { repository.readFile(testPath, 0L, any(), testStorageType, testServerId) }
    }

    /**
     * Use Case: Re-use cached buffer for subsequent sequential reads
     * Given an initial read that loaded a 512KB chunk
     * When a second readAt is called at position within the cached chunk
     * Then it should serve from memory without requesting repository again
     */
    @Test
    fun testSequentialReadUsesCachedBuffer() = runBlocking {
        // Given
        val fakeChunk = ByteArray(4096) { (it % 50).toByte() }
        coEvery {
            repository.readFile(testPath, 0L, any(), testStorageType, testServerId)
        } returns fakeChunk

        val dataSource = StorageMediaDataSource(
            repository = repository,
            path = testPath,
            storageType = testStorageType,
            serverId = testServerId,
            size = testFileSize,
        )

        val targetBuffer1 = ByteArray(16)
        val targetBuffer2 = ByteArray(16)

        // When
        dataSource.readAt(0L, targetBuffer1, 0, 16)
        dataSource.readAt(16L, targetBuffer2, 0, 16)

        // Then
        coVerify(exactly = 1) { repository.readFile(testPath, 0L, any(), testStorageType, testServerId) }
    }

    /**
     * Use Case: Return EOF (-1) when position is equal or greater than size
     * Given position >= size
     * When readAt is called
     * Then it should return -1
     */
    @Test
    fun testReadAtAtEof() {
        val dataSource = StorageMediaDataSource(
            repository = repository,
            path = testPath,
            storageType = testStorageType,
            serverId = testServerId,
            size = testFileSize,
        )

        val targetBuffer = ByteArray(16)
        val bytesRead = dataSource.readAt(testFileSize, targetBuffer, 0, 16)

        assertEquals(-1, bytesRead)
    }

    @Test
    fun testReadAtRejectsInvalidBufferRange() {
        val dataSource = StorageMediaDataSource(
            repository = repository,
            path = testPath,
            storageType = testStorageType,
            serverId = testServerId,
            size = testFileSize,
        )

        val bytesRead = dataSource.readAt(0L, ByteArray(16), 8, 16)

        assertEquals(-1, bytesRead)
    }

    /**
     * Use Case: Safely close media data source
     * Given an active media data source
     * When close is called
     * Then subsequent readAt calls should return EOF without throwing or blocking
     */
    @Test
    fun testClose() {
        val dataSource = StorageMediaDataSource(
            repository = repository,
            path = testPath,
            storageType = testStorageType,
            serverId = testServerId,
            size = testFileSize,
        )

        dataSource.close()
        val targetBuffer = ByteArray(16)
        val bytesRead = dataSource.readAt(0L, targetBuffer, 0, 16)

        assertEquals(-1, bytesRead)
    }
}
