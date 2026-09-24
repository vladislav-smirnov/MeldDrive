package io.github.airdaydreamers.melddrive

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.util.VideoFrameFetcher
import io.github.airdaydreamers.melddrive.util.VideoThumbnailKeyer
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class MelddriveApplication :
    Application(),
    SingletonImageLoader.Factory {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ApplicationEntryPoint {
        fun fileRepository(): FileRepository
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val entryPoint = EntryPointAccessors.fromApplication(this, ApplicationEntryPoint::class.java)
        val fileRepository = entryPoint.fileRepository()

        return ImageLoader.Builder(context)
            .components {
                add(VideoThumbnailKeyer())
                add(VideoFrameFetcher.ModelFactory(fileRepository))
                add(VideoFrameFetcher.UriFactory(this@MelddriveApplication))
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("video_thumbnails").toOkioPath())
                    .maxSizeBytes(MAX_DISK_CACHE_BYTES)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_MAX_SIZE_PERCENT)
                    .build()
            }
            .build()
    }

    companion object {
        private const val MEMORY_CACHE_MAX_SIZE_PERCENT = 0.25
        private const val MAX_DISK_CACHE_BYTES = 100L * 1024L * 1024L // 100MB
    }
}
