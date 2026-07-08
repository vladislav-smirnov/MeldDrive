package io.github.airdaydreamers.melddrive.data.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import androidx.collection.LruCache
import io.github.airdaydreamers.melddrive.data.db.AppDatabase
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.security.SecurityManager
import io.github.airdaydreamers.melddrive.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.IOException

class FileStreamProvider : ContentProvider() {

    private lateinit var repository: FileRepository
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    private lateinit var settingsManager: SettingsManager
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val database = AppDatabase.getDatabase(context)
        val securityManager = SecurityManager(context)
        val credentialStorage = CredentialStorage(context, securityManager)
        settingsManager = SettingsManager(context)
        repository = FileRepository(database.remoteServerDao(), credentialStorage)
        handlerThread = HandlerThread("FileStreamProviderThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? {
        val path = uri.path?.substringAfterLast(".", "")
        return if (path.isNullOrEmpty()) "*/*" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(path)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val segments = uri.pathSegments
        if (segments.size < MIN_URI_SEGMENTS) throw FileNotFoundException("Invalid URI: $uri")

        val storageType = StorageType.valueOf(segments[0])
        val serverId = segments[1].toLong().let { if (it == -1L) null else it }
        val filePath = segments.subList(2, segments.size).joinToString("/")

        val metaData = runBlocking {
            val size = repository.getFileSize(filePath, storageType, serverId)
            val bSize = settingsManager.bufferSizeFlow.first() * BYTES_IN_MB
            val aggressive = settingsManager.isAggressiveBufferingEnabledFlow.first()
            FileStreamMetadata(size, bSize, aggressive, filePath, storageType, serverId)
        }

        val storageManager = context?.getSystemService(StorageManager::class.java)
            ?: throw FileNotFoundException("StorageManager not found")

        return getFileDescriptor(storageManager, mode, metaData)
    }

    private fun getFileDescriptor(storageManager: StorageManager, mode: String, metaData: FileStreamMetadata): ParcelFileDescriptor {
        val thread = HandlerThread("FileStream-${metaData.filePath}")
        thread.start()
        val pfdHandler = Handler(thread.looper)

        return try {
            storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.parseMode(mode),
                FileStreamCallbackV2(
                    repository,
                    metaData.filePath,
                    metaData.storageType,
                    metaData.serverId,
                    metaData.fileSize,
                    metaData.bufferSize,
                    metaData.isAggressive,
                    chunkCache,
                    providerScope,
                    thread,
                ),
                pfdHandler,
            )
        } catch (ignored: IOException) {
            thread.quitSafely()
            throw FileNotFoundException("Failed to open proxy file descriptor")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            chunkCache.evictAll()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        chunkCache.evictAll()
    }

    private val chunkCache by lazy {
        object : LruCache<String, ByteArray>(MAX_CACHE_SIZE) {
            override fun sizeOf(key: String, value: ByteArray): Int = value.size
        }
    }

    private class FileStreamCallbackV2(
        private val repository: FileRepository,
        private val path: String,
        private val storageType: StorageType,
        private val serverId: Long?,
        private val size: Long,
        private val bufferSize: Int,
        private val isAggressive: Boolean,
        private val cache: LruCache<String, ByteArray>,
        private val scope: CoroutineScope,
        private val handlerThread: HandlerThread,
    ) : ProxyFileDescriptorCallback() {

        private var prefetchJob: Job? = null
        private var lastPrefetchIndex: Long = -1

        override fun onGetSize(): Long = size

        @Suppress("TooGenericExceptionCaught")
        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            if (size <= 0 || offset >= this.size) return 0

            return try {
                val totalRead = readFromRepository(offset, size, data)
                if (isAggressive) {
                    prefetchNextChunks(offset / bufferSize + 1)
                }
                totalRead
            } catch (e: ErrnoException) {
                throw e
            } catch (e: IOException) {
                Log.e("FileStreamProvider", "Read failed at offset $offset: ${e.message}")
                throw ErrnoException("onRead", OsConstants.EIO)
            } catch (e: RuntimeException) {
                Log.e("FileStreamProvider", "Read failed at offset $offset: ${e.message}")
                throw ErrnoException("onRead", OsConstants.EIO)
            }
        }

        private fun readFromRepository(offset: Long, size: Int, data: ByteArray): Int {
            var totalRead = 0
            var currentOffset = offset

            while (totalRead < size && currentOffset < this.size) {
                val chunkIndex = currentOffset / bufferSize
                val offsetInChunk = (currentOffset % bufferSize).toInt()

                val chunk = getChunk(chunkIndex, offsetInChunk)
                val availableInChunk = chunk?.let { it.size - offsetInChunk } ?: 0

                if (availableInChunk <= 0) {
                    break
                }

                val toCopy = Math.min(size - totalRead, availableInChunk)
                System.arraycopy(chunk!!, offsetInChunk, data, totalRead, toCopy)

                totalRead += toCopy
                currentOffset += toCopy
            }
            return totalRead
        }

        private fun getChunk(chunkIndex: Long, offsetInChunk: Int): ByteArray? {
            val cacheKey = "$storageType-$serverId-$path-$chunkIndex"
            val cached = synchronized(cache) {
                val entry = cache[cacheKey]
                if (entry != null) {
                    val isLastChunk = (chunkIndex + 1) * bufferSize >= this.size
                    if (offsetInChunk >= entry.size && !isLastChunk) {
                        cache.remove(cacheKey)
                        null
                    } else {
                        entry
                    }
                } else {
                    null
                }
            }
            return cached ?: fetchIntoBuffer(chunkIndex, cacheKey)
        }

        private fun fetchIntoBuffer(chunkIndex: Long, cacheKey: String): ByteArray? {
            val fetched = runBlocking {
                repository.readFile(path, chunkIndex * bufferSize, bufferSize, storageType, serverId)
            }
            if (fetched.isNotEmpty()) {
                synchronized(cache) {
                    cache.put(cacheKey, fetched)
                }
                return fetched
            }
            return null
        }

        @Suppress("TooGenericExceptionCaught")
        private fun prefetchNextChunks(startIndex: Long) {
            if (lastPrefetchIndex == startIndex && prefetchJob?.isActive == true) return

            prefetchJob?.cancel()
            lastPrefetchIndex = startIndex

            prefetchJob = scope.launch {
                for (i in 0 until PREFETCH_COUNT) {
                    val nextIndex = startIndex + i
                    if (nextIndex * bufferSize >= size) break

                    val nextCacheKey = "$storageType-$serverId-$path-$nextIndex"
                    val cached = synchronized(cache) { cache[nextCacheKey] }

                    // Only prefetch if not in cache or if cached is partial
                    val isLastChunk = (nextIndex + 1) * bufferSize >= size
                    if (cached == null || (cached.size < bufferSize && !isLastChunk)) {
                        try {
                            val nextChunk = repository.readFile(path, nextIndex * bufferSize, bufferSize, storageType, serverId)
                            if (nextChunk.isNotEmpty()) {
                                synchronized(cache) {
                                    cache.put(nextCacheKey, nextChunk)
                                }
                            }
                        } catch (e: IOException) {
                            Log.e("FileStreamCallback", "Prefetch failed for index $nextIndex: ${e.message}")
                        } catch (e: RuntimeException) {
                            Log.e("FileStreamCallback", "Prefetch failed for index $nextIndex: ${e.message}")
                        }
                    }
                }
            }
        }

        override fun onRelease() {
            prefetchJob?.cancel()
            handlerThread.quitSafely()
        }
    }

    private data class FileStreamMetadata(
        val fileSize: Long,
        val bufferSize: Int,
        val isAggressive: Boolean,
        val filePath: String,
        val storageType: StorageType,
        val serverId: Long?,
    )

    companion object {
        const val AUTHORITY = "io.github.airdaydreamers.melddrive.filestream"
        private const val MIN_URI_SEGMENTS = 3
        private const val BYTES_IN_MB = 1024 * 1024
        private const val MAX_CACHE_SIZE = 100 * BYTES_IN_MB
        private const val PREFETCH_COUNT = 3

        fun buildUri(storageType: StorageType, serverId: Long?, path: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(storageType.name)
            .appendPath((serverId ?: -1L).toString())
            .appendEncodedPath(path)
            .build()
    }
}
