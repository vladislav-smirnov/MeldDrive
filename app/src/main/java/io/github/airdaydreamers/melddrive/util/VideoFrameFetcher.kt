package io.github.airdaydreamers.melddrive.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Scale
import coil3.size.pxOrElse
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.model.VideoThumbnailModel
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.storage.StorageMediaDataSource
import timber.log.Timber
import java.io.BufferedOutputStream
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class VideoFrameFetcher(
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val cacheKey: String?,
    private val dataSource: MediaDataSource? = null,
    private val setDataSource: MediaMetadataRetriever.() -> Unit,
) : Fetcher {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetch(): FetchResult? {
        Timber.d("VideoFrameFetcher: Starting fetch for cacheKey=%s", cacheKey)
        val cachedResult = checkDiskCache()
        if (cachedResult != null) return cachedResult

        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource()

                val bitmap = retriever.embeddedPicture?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                } ?: extractFrameBitmap(retriever)

                if (bitmap != null) {
                    Timber.d("VideoFrameFetcher: Decoded frame (%dx%d) for cacheKey=%s", bitmap.width, bitmap.height, cacheKey)
                    saveToDiskCache(bitmap)
                    ImageFetchResult(
                        image = bitmap.toDrawable(options.context.resources).asImage(),
                        isSampled = false,
                        dataSource = DataSource.DISK,
                    )
                } else {
                    Timber.w("VideoFrameFetcher: Failed to decode frame for cacheKey=%s", cacheKey)
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "VideoFrameFetcher: Error fetching frame for cacheKey=%s", cacheKey)
            null
        } finally {
            try {
                dataSource?.close()
            } catch (_: Exception) {}
        }
    }

    private fun checkDiskCache(): FetchResult? {
        val diskCache = imageLoader.diskCache
        val diskKey = options.diskCacheKey ?: cacheKey
        if (diskCache == null || diskKey == null || !options.diskCachePolicy.readEnabled) {
            return null
        }

        val snapshot = diskCache.openSnapshot(diskKey)
        val cachedBitmap = try {
            snapshot?.use { BitmapFactory.decodeFile(it.data.toFile().absolutePath) }
        } catch (_: Exception) {
            null
        }

        return cachedBitmap?.let {
            Timber.d("VideoFrameFetcher: Disk cache hit for cacheKey=%s", cacheKey)
            ImageFetchResult(
                image = it.toDrawable(options.context.resources).asImage(),
                isSampled = false,
                dataSource = DataSource.DISK,
            )
        }
    }

    private fun saveToDiskCache(bitmap: Bitmap) {
        val diskCache = imageLoader.diskCache
        val diskKey = options.diskCacheKey ?: cacheKey
        if (diskCache != null && diskKey != null && options.diskCachePolicy.writeEnabled) {
            val editor = diskCache.openEditor(diskKey) ?: return
            writeBitmapToEditor(editor, bitmap)
        }
    }

    private fun writeBitmapToEditor(editor: coil3.disk.DiskCache.Editor, bitmap: Bitmap) {
        try {
            editor.data.toFile().outputStream().use { fileOut ->
                BufferedOutputStream(fileOut).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, out)
                    out.flush()
                }
            }
            editor.commit()
        } catch (_: Exception) {
            try {
                editor.abort()
            } catch (_: Exception) {
                editor.abort()
            }
        }
    }

    private fun extractFrameBitmap(retriever: MediaMetadataRetriever): Bitmap? {
        val (srcWidth, srcHeight) = extractDimensions(retriever)
        val durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L

        val frameMicros = if (durationMillis > 0) {
            TimeUnit.MILLISECONDS.toMicros((FRAME_PERCENT * durationMillis).roundToLong())
        } else {
            FALLBACK_FRAME_MICROS
        }

        val (width, height) = calculateTargetDimensions(srcWidth, srcHeight)
        return decodeFrame(retriever, frameMicros, width, height, srcWidth, srcHeight)
    }

    private fun extractDimensions(retriever: MediaMetadataRetriever): Pair<Int, Int> {
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0

        return if (rotation == ROTATION_90 || rotation == ROTATION_270) {
            Pair(height, width)
        } else {
            Pair(width, height)
        }
    }

    private fun calculateTargetDimensions(srcWidth: Int, srcHeight: Int): Pair<Int, Int> {
        val dstWidth = options.size.width.pxOrElse { if (srcWidth > 0) srcWidth else DEFAULT_DIMENSION_PX }
        val dstHeight = options.size.height.pxOrElse { if (srcHeight > 0) srcHeight else DEFAULT_DIMENSION_PX }

        val rawScale = computeSizeMultiplier(
            srcWidth = if (srcWidth > 0) srcWidth else dstWidth,
            srcHeight = if (srcHeight > 0) srcHeight else dstHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
        )

        val scale = rawScale.coerceAtMost(1.0)
        val width = (scale * (if (srcWidth > 0) srcWidth else dstWidth)).roundToInt().coerceAtLeast(1)
        val height = (scale * (if (srcHeight > 0) srcHeight else dstHeight)).roundToInt().coerceAtLeast(1)
        return Pair(width, height)
    }

    private fun decodeFrame(retriever: MediaMetadataRetriever, timeUs: Long, width: Int, height: Int, srcWidth: Int, srcHeight: Int): Bitmap? {
        val frameOption = MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && srcWidth > 0 && srcHeight > 0) {
            retriever.getScaledFrameAtTime(timeUs, frameOption, width, height)
        } else {
            retriever.getFrameAtTime(timeUs, frameOption)
        }
    }

    private fun computeSizeMultiplier(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, scale: Scale): Double {
        val widthPercent = dstWidth.toDouble() / srcWidth
        val heightPercent = dstHeight.toDouble() / srcHeight
        return when (scale) {
            Scale.FILL -> maxOf(widthPercent, heightPercent)
            Scale.FIT -> minOf(widthPercent, heightPercent)
        }
    }

    class ModelFactory(private val repository: FileRepository) : Fetcher.Factory<VideoThumbnailModel> {
        override fun create(data: VideoThumbnailModel, options: Options, imageLoader: ImageLoader): Fetcher? {
            val file = data.file
            if (file.isDirectory || !MimeTypeMapCompat.isVideoFile(file.name)) {
                return null
            }

            val cacheKey = "video_thumb_${file.storageType.name}_${data.serverId ?: -1L}_${file.path}_${file.lastModified}_${file.size}"

            return if (file.storageType == StorageType.LOCAL) {
                VideoFrameFetcher(options, imageLoader, cacheKey) {
                    setDataSource(file.path)
                }
            } else {
                val mediaDataSource = StorageMediaDataSource(
                    repository = repository,
                    path = file.path,
                    storageType = file.storageType,
                    serverId = data.serverId,
                    size = file.size,
                )
                VideoFrameFetcher(options, imageLoader, cacheKey, dataSource = mediaDataSource) {
                    setDataSource(mediaDataSource)
                }
            }
        }
    }

    class UriFactory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            val mimeType = context.contentResolver.getType(data) ?: MimeTypeMapCompat.getMimeType(data.toString())
            val isSupportedScheme = scheme == "content" || scheme == "file"
            val isVideo = MimeTypeMapCompat.isVideoFile(data.toString()) || mimeType.startsWith("video/")

            if (!isVideo || !isSupportedScheme) {
                return null
            }

            return VideoFrameFetcher(options, imageLoader, cacheKey = data.toString()) {
                setDataSource(context, data)
            }
        }
    }

    companion object {
        private const val ROTATION_90 = 90
        private const val ROTATION_270 = 270
        private const val DEFAULT_DIMENSION_PX = 512
        private const val FRAME_PERCENT = 1.0 / 3.0
        private const val FALLBACK_FRAME_MICROS = 1_000_000L
        private const val COMPRESS_QUALITY = 85
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <R> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val autoCloseable: AutoCloseable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this
    } else {
        AutoCloseable {
            try {
                release()
            } catch (_: Exception) {}
        }
    }
    return autoCloseable.use { block(this) }
}
