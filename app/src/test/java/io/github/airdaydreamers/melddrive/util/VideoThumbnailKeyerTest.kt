package io.github.airdaydreamers.melddrive.util

import coil3.request.Options
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.model.VideoThumbnailModel
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [VideoThumbnailKeyer] verifying cache key generation and uniqueness.
 */
class VideoThumbnailKeyerTest {

    private lateinit var keyer: VideoThumbnailKeyer
    private lateinit var options: Options

    @BeforeEach
    fun setUp() {
        keyer = VideoThumbnailKeyer()
        options = mockk(relaxed = true)
    }

    /**
     * Use Case: Generate deterministic cache key for VideoThumbnailModel
     * Given a VideoThumbnailModel
     * When key is called
     * Then it should return a deterministic key based on the file identity
     */
    @Test
    fun testKeyGeneration() {
        val file = FileItem(
            path = "movies/clip.mp4",
            name = "clip.mp4",
            isDirectory = false,
            size = 1048576L,
            lastModified = 1600000000L,
            storageType = StorageType.SMB,
        )
        val model = VideoThumbnailModel(file = file, serverId = 42L)

        val key = keyer.key(model, options)

        assertEquals("video_thumb_SMB_42_movies/clip.mp4_1600000000_1048576", key)
    }

    /**
     * Use Case: Ensure distinct keys for different servers/paths
     * Given two VideoThumbnailModels with different serverId
     * When key is called for each
     * Then the generated keys should be different
     */
    @Test
    fun testKeyUniquenessForDifferentServers() {
        val file = FileItem(
            path = "movies/clip.mp4",
            name = "clip.mp4",
            isDirectory = false,
            size = 1048576L,
            lastModified = 1600000000L,
            storageType = StorageType.SMB,
        )
        val model1 = VideoThumbnailModel(file = file, serverId = 1L)
        val model2 = VideoThumbnailModel(file = file, serverId = 2L)

        val key1 = keyer.key(model1, options)
        val key2 = keyer.key(model2, options)

        assertNotEquals(key1, key2)
    }
}
