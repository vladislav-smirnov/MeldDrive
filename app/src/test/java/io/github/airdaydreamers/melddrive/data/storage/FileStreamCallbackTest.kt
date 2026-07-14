package io.github.airdaydreamers.melddrive.data.storage

import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FileStreamCallback] using Robolectric.
 * Verifies ranged read behaviors and chunk buffering/prefetching logic
 * on a class that extends the Android platform [android.os.ProxyFileDescriptorCallback].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FileStreamCallbackTest {

    private lateinit var mockRepository: FileRepository

    @Before
    fun setUp() {
        mockRepository = mockk(relaxed = true)

        // Set up default answer for readFile to prevent ClassCastException on non-stubbed reads (e.g. prefetching)
        coEvery {
            mockRepository.readFile(any(), any(), any(), any(), any())
        } answers {
            val length = arg<Int>(2)
            ByteArray(length) { 1 }
        }
    }

    /**
     * Use Case: Unbuffered Ranged Reading
     * Given buffering is disabled
     * When onRead is called
     * Then it should fetch the requested range from the repository
     */
    @Test
    fun testUnbufferedRead() {
        // Given
        val callback = FileStreamCallback(
            repository = mockRepository,
            path = "movies/film.mp4",
            storageType = StorageType.LOCAL,
            serverId = null,
            size = 5000L,
            bufferingEnabled = false,
            bufferSizeMb = 16,
        )

        val outputBuffer = ByteArray(10)
        coEvery {
            mockRepository.readFile("movies/film.mp4", 0L, any(), StorageType.LOCAL, null)
        } returns "0123456789".toByteArray()

        // When
        val bytesRead = callback.onRead(0L, 10, outputBuffer)

        // Then
        assertEquals(10, bytesRead)
        assertEquals("0123456789", String(outputBuffer))
    }

    /**
     * Use Case: Buffered Read (SMB Chunks)
     * Given buffering is enabled for SMB storage and chunk size is 1MB
     * When onRead is called
     * Then it should download the corresponding 1MB chunk from the repository and cache it
     */
    @Test
    fun testBufferedRead() = runBlocking {
        // Given
        val callback = FileStreamCallback(
            repository = mockRepository,
            path = "share/video.mkv",
            storageType = StorageType.SMB,
            serverId = 2L,
            size = 5000000L, // 5MB
            bufferingEnabled = true,
            bufferSizeMb = 4,
        )

        val chunk0Data = ByteArray(FileStreamCallback.CHUNK_SIZE) { 5 }
        coEvery {
            mockRepository.readFile("share/video.mkv", 0L, FileStreamCallback.CHUNK_SIZE, StorageType.SMB, 2L)
        } returns chunk0Data

        val outputBuffer = ByteArray(5)

        // When
        val bytesRead = callback.onRead(0L, 5, outputBuffer)

        // Then
        assertEquals(5, bytesRead)
        assertEquals(5.toByte(), outputBuffer[0])
        assertEquals(5.toByte(), outputBuffer[4])

        // Verify chunk 0 was read from the repository
        coVerify {
            mockRepository.readFile("share/video.mkv", 0L, FileStreamCallback.CHUNK_SIZE, StorageType.SMB, 2L)
        }
    }
}
