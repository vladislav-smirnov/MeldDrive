package io.github.airdaydreamers.melddrive.util

import coil3.ImageLoader
import coil3.request.Options
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.model.VideoThumbnailModel
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [VideoFrameFetcher] ModelFactory and UriFactory validating
 * fetcher instantiation and filtering rules.
 */
class VideoFrameFetcherTest {

    private lateinit var repository: FileRepository
    private lateinit var options: Options
    private lateinit var imageLoader: ImageLoader

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        options = mockk(relaxed = true)
        imageLoader = mockk(relaxed = true)
    }

    /**
     * Use Case: Create fetcher for local video file
     * Given a VideoThumbnailModel representing a local mp4 file
     * When ModelFactory.create is called
     * Then it should return a non-null VideoFrameFetcher
     */
    @Test
    fun testModelFactoryCreatesFetcherForLocalVideo() {
        val modelFactory = VideoFrameFetcher.ModelFactory(repository)
        val localVideoItem = FileItem(
            path = "/storage/emulated/0/video.mp4",
            name = "video.mp4",
            isDirectory = false,
            storageType = StorageType.LOCAL,
        )
        val model = VideoThumbnailModel(file = localVideoItem)

        val fetcher = modelFactory.create(model, options, imageLoader)

        assertNotNull(fetcher)
    }

    /**
     * Use Case: Create fetcher for SMB remote video file
     * Given a VideoThumbnailModel representing an SMB remote mkv file
     * When ModelFactory.create is called
     * Then it should return a non-null VideoFrameFetcher
     */
    @Test
    fun testModelFactoryCreatesFetcherForSmbVideo() {
        val modelFactory = VideoFrameFetcher.ModelFactory(repository)
        val smbVideoItem = FileItem(
            path = "share/movie.mkv",
            name = "movie.mkv",
            isDirectory = false,
            size = 50_000_000L,
            storageType = StorageType.SMB,
        )
        val model = VideoThumbnailModel(file = smbVideoItem, serverId = 1L)

        val fetcher = modelFactory.create(model, options, imageLoader)

        assertNotNull(fetcher)
    }

    /**
     * Use Case: Reject directory items in ModelFactory
     * Given a VideoThumbnailModel representing a folder
     * When ModelFactory.create is called
     * Then it should return null
     */
    @Test
    fun testModelFactoryRejectsDirectories() {
        val modelFactory = VideoFrameFetcher.ModelFactory(repository)
        val folderItem = FileItem(
            path = "/storage/emulated/0/Movies",
            name = "Movies",
            isDirectory = true,
            storageType = StorageType.LOCAL,
        )
        val model = VideoThumbnailModel(file = folderItem)

        val fetcher = modelFactory.create(model, options, imageLoader)

        assertNull(fetcher)
    }

    /**
     * Use Case: Reject non-video files in ModelFactory
     * Given a VideoThumbnailModel representing a text file
     * When ModelFactory.create is called
     * Then it should return null
     */
    @Test
    fun testModelFactoryRejectsNonVideoFiles() {
        val modelFactory = VideoFrameFetcher.ModelFactory(repository)
        val textItem = FileItem(
            path = "/storage/emulated/0/notes.txt",
            name = "notes.txt",
            isDirectory = false,
            storageType = StorageType.LOCAL,
        )
        val model = VideoThumbnailModel(file = textItem)

        val fetcher = modelFactory.create(model, options, imageLoader)

        assertNull(fetcher)
    }
}
