package io.github.airdaydreamers.melddrive.data.storage

import android.media.MediaDataSource
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class StorageMediaDataSource(
    private val repository: FileRepository,
    private val path: String,
    private val storageType: StorageType,
    private val serverId: Long?,
    private val size: Long,
) : MediaDataSource() {

    @Volatile
    private var isClosed = false

    @Volatile
    private var bufferStart: Long = -1L

    @Volatile
    private var bufferData: ByteArray? = null

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (isClosed) return EOF
        Timber.d("StorageMediaDataSource: readAt position=%d, size=%d, offset=%d, path=%s", position, size, offset, path)
        val isValidRead = !isClosed &&
            position >= 0L &&
            position < this.size &&
            offset >= 0 &&
            size > 0 &&
            offset <= buffer.size &&
            size <= buffer.size - offset
        if (!isValidRead) return EOF

        val bytesToRead = size.toLong().coerceAtMost(this.size - position).toInt()

        if (!isPositionInBuffer(position, bytesToRead)) {
            fetchBufferChunk(position)
        }

        if (isClosed) return EOF

        val currentBuffer = bufferData
        val currentBufferStart = bufferStart
        val localOffset = if (currentBuffer != null) (position - currentBufferStart).toInt() else 0
        if (currentBuffer == null || localOffset < 0 || localOffset >= currentBuffer.size) return EOF

        val available = (currentBuffer.size - localOffset).coerceAtLeast(0)
        val actualRead = bytesToRead.coerceAtMost(available)

        return if (actualRead > 0) {
            System.arraycopy(currentBuffer, localOffset, buffer, offset, actualRead)
            actualRead
        } else {
            EOF
        }
    }

    private fun isPositionInBuffer(position: Long, requestedSize: Int): Boolean {
        val currentBuffer = bufferData ?: return false
        val currentBufferStart = bufferStart
        val bufferEnd = currentBufferStart + currentBuffer.size
        return position >= currentBufferStart && (position + requestedSize) <= bufferEnd
    }

    @Suppress("TooGenericExceptionCaught")
    private fun fetchBufferChunk(position: Long) {
        if (isClosed) return
        val readSize = CHUNK_SIZE.toLong().coerceAtMost(size - position).toInt()
        if (readSize <= 0) return

        Timber.d("StorageMediaDataSource: Fetching buffer chunk path=%s, position=%d, readSize=%d", path, position, readSize)
        val chunk = try {
            runBlocking {
                repository.readFile(path, position, readSize, storageType, serverId)
            }
        } catch (e: Exception) {
            Timber.e(e, "StorageMediaDataSource: Failed to fetch buffer chunk path=%s at position=%d", path, position)
            null
        }

        if (!isClosed) {
            bufferStart = position
            bufferData = chunk
        }
    }

    override fun getSize(): Long = size

    override fun close() {
        Timber.d("StorageMediaDataSource: Closing datasource for path=%s", path)
        isClosed = true
        bufferData = null
        bufferStart = -1L
    }

    companion object {
        private const val EOF = -1
        private const val CHUNK_SIZE = 512 * 1024
    }
}
