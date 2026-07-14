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
import android.util.LruCache
import io.github.airdaydreamers.melddrive.data.db.AppDatabase
import io.github.airdaydreamers.melddrive.data.model.MeldDriveException
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class FileStreamProvider : ContentProvider() {

    private lateinit var repository: FileRepository
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val database = AppDatabase.getDatabase(context)
        val securityManager = SecurityManager(context)
        val credentialStorage = CredentialStorage(context, securityManager)
        repository = FileRepository(database.remoteServerDao(), credentialStorage)
        handlerThread = HandlerThread("FileStreamProviderThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? {
        val path = uri.path?.substringAfterLast(".", "")
        return if (path.isNullOrEmpty()) "*/*" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(path)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val segments = uri.pathSegments
        if (segments.size < MIN_URI_SEGMENTS) throw FileNotFoundException("Invalid URI: $uri")

        val storageType = StorageType.valueOf(segments[0])
        val serverId = segments[1].toLong().let { if (it == -1L) null else it }
        val filePath = segments.subList(2, segments.size).joinToString("/")

        val settingsManager = SettingsManager(context ?: throw FileNotFoundException("Context not found"))
        val bufferingEnabled = runBlocking { settingsManager.bufferingEnabled.first() }
        val bufferSizeMb = runBlocking { settingsManager.bufferSizeMb.first() }

        val fileSize = runBlocking {
            repository.getFileSize(filePath, storageType, serverId)
        }

        val storageManager = context?.getSystemService(StorageManager::class.java)
            ?: throw FileNotFoundException("StorageManager not found")

        return try {
            storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.parseMode(mode),
                FileStreamCallback(
                    repository = repository,
                    path = filePath,
                    storageType = storageType,
                    serverId = serverId,
                    size = fileSize,
                    bufferingEnabled = bufferingEnabled,
                    bufferSizeMb = bufferSizeMb,
                ),
                handler,
            )
        } catch (ignored: IOException) {
            throw FileNotFoundException("Failed to open proxy file descriptor")
        }
    }

    private class FileStreamCallback(
        private val repository: FileRepository,
        private val path: String,
        private val storageType: StorageType,
        private val serverId: Long?,
        private val size: Long,
        private val bufferingEnabled: Boolean,
        private val bufferSizeMb: Int,
    ) : ProxyFileDescriptorCallback() {

        private var buffer: ByteArray? = null
        private var bufferOffset: Long = -1L
        private val bufferSize = DEFAULT_BUFFER_SIZE

        private val isBufferActive = bufferingEnabled && storageType == StorageType.SMB
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val prefetchSemaphore = Semaphore(MAX_CONCURRENT_PREFETCH)
        private val activeDownloads = ConcurrentHashMap<Long, Deferred<ByteArray>>()

        private val cache = object : LruCache<Long, ByteArray>(bufferSizeMb) {
            override fun sizeOf(key: Long, value: ByteArray): Int = 1
        }

        override fun onGetSize(): Long = size

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            if (size <= 0) return 0

            return try {
                if (isBufferActive) {
                    readWithBuffering(offset, size, data)
                } else {
                    readFromRepository(offset, size, data)
                }
            } catch (ignored: IOException) {
                throw ErrnoException("onRead", OsConstants.EIO)
            } catch (ignored: MeldDriveException) {
                throw ErrnoException("onRead", OsConstants.EIO)
            }
        }

        private fun readWithBuffering(offset: Long, size: Int, data: ByteArray): Int {
            val fileTotalSize = this@FileStreamCallback.size
            if (offset >= fileTotalSize) return 0

            val startChunk = offset / CHUNK_SIZE
            val endChunk = (offset + size - 1) / CHUNK_SIZE

            var totalBytesRead = 0

            triggerPrefetch(startChunk)

            for (chunkIndex in startChunk..endChunk) {
                val chunkData = getChunk(chunkIndex)
                if (chunkData.isEmpty()) {
                    break
                }

                val chunkStartOffset = chunkIndex * CHUNK_SIZE
                val readStartOffset = Math.max(offset, chunkStartOffset)
                val readEndOffset = Math.min(offset + size, chunkStartOffset + chunkData.size)
                val copyLength = (readEndOffset - readStartOffset).toInt()

                if (copyLength > 0) {
                    val startInChunk = (readStartOffset - chunkStartOffset).toInt()
                    val startInOutput = (readStartOffset - offset).toInt()
                    System.arraycopy(chunkData, startInChunk, data, startInOutput, copyLength)
                    totalBytesRead += copyLength
                }
            }

            return totalBytesRead
        }

        private fun getChunk(chunkIndex: Long): ByteArray {
            val cached = synchronized(cache) { cache.get(chunkIndex) }
            if (cached != null) {
                return cached
            }

            val resultDeferred = synchronized(activeDownloads) {
                activeDownloads[chunkIndex] ?: activeDownloads.getOrPut(chunkIndex) {
                    scope.async {
                        try {
                            performDownload(chunkIndex)
                        } finally {
                            synchronized(activeDownloads) {
                                activeDownloads.remove(chunkIndex)
                            }
                        }
                    }
                }
            }

            return runBlocking { resultDeferred.await() }
        }

        private fun prefetchChunk(chunkIndex: Long) {
            synchronized(activeDownloads) {
                if (activeDownloads.containsKey(chunkIndex)) return
                val isCached = synchronized(cache) { cache.get(chunkIndex) != null }
                if (isCached) return

                val deferred = scope.async {
                    try {
                        prefetchSemaphore.withPermit {
                            val stillCached = synchronized(cache) { cache.get(chunkIndex) != null }
                            if (!stillCached) {
                                performDownload(chunkIndex)
                            } else {
                                ByteArray(0)
                            }
                        }
                    } finally {
                        synchronized(activeDownloads) {
                            activeDownloads.remove(chunkIndex)
                        }
                    }
                }
                activeDownloads[chunkIndex] = deferred
            }
        }

        private fun triggerPrefetch(currentChunkIndex: Long) {
            val fileTotalSize = this@FileStreamCallback.size
            val maxChunksToPrefetch = bufferSizeMb

            scope.launch {
                for (i in 1..maxChunksToPrefetch) {
                    val targetChunkIndex = currentChunkIndex + i
                    val targetOffset = targetChunkIndex * CHUNK_SIZE
                    if (targetOffset >= fileTotalSize) break

                    prefetchChunk(targetChunkIndex)
                }
            }
        }

        private suspend fun performDownload(chunkIndex: Long): ByteArray {
            val cached = synchronized(cache) { cache.get(chunkIndex) }
            if (cached != null) {
                return cached
            }

            val chunkOffset = chunkIndex * CHUNK_SIZE
            val fileTotalSize = this@FileStreamCallback.size
            val chunkLength = Math.min(CHUNK_SIZE.toLong(), fileTotalSize - chunkOffset).toInt()

            val result = if (chunkLength <= 0) {
                ByteArray(0)
            } else {
                val fetched = repository.readFile(path, chunkOffset, chunkLength, storageType, serverId)
                if (fetched.isNotEmpty()) {
                    synchronized(cache) {
                        cache.put(chunkIndex, fetched)
                    }
                }
                fetched
            }
            return result
        }

        private fun readFromRepository(offset: Long, size: Int, data: ByteArray): Int {
            val currentBuffer = buffer
            return if (currentBuffer != null && offset >= bufferOffset && offset + size <= bufferOffset + currentBuffer.size) {
                val startInBuf = (offset - bufferOffset).toInt()
                System.arraycopy(currentBuffer, startInBuf, data, 0, size)
                size
            } else if (size >= bufferSize) {
                val bytes = runBlocking {
                    repository.readFile(path, offset, size, storageType, serverId)
                }
                val copySize = Math.min(size, bytes.size)
                System.arraycopy(bytes, 0, data, 0, copySize)
                copySize
            } else {
                fetchIntoBuffer(offset, size, data)
            }
        }

        private fun fetchIntoBuffer(offset: Long, size: Int, data: ByteArray): Int {
            val fetched = runBlocking {
                repository.readFile(path, offset, bufferSize, storageType, serverId)
            }
            return if (fetched.isEmpty()) {
                0
            } else {
                buffer = fetched
                bufferOffset = offset
                val copySize = Math.min(size, fetched.size)
                System.arraycopy(fetched, 0, data, 0, copySize)
                copySize
            }
        }

        override fun onRelease() {
            scope.cancel()
            synchronized(cache) {
                cache.evictAll()
            }
            synchronized(activeDownloads) {
                activeDownloads.clear()
            }
            buffer = null
        }
    }

    companion object {
        const val AUTHORITY = "io.github.airdaydreamers.melddrive.filestream"
        private const val DEFAULT_BUFFER_SIZE = 1048576 // 1MB
        const val CHUNK_SIZE = 1048576 // 1MB
        private const val MIN_URI_SEGMENTS = 3
        private const val MAX_CONCURRENT_PREFETCH = 4

        fun buildUri(storageType: StorageType, serverId: Long?, path: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(storageType.name)
            .appendPath((serverId ?: -1L).toString())
            .appendEncodedPath(path)
            .build()
    }
}
