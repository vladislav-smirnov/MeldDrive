package io.github.airdaydreamers.melddrive.data.storage

import android.media.MediaDataSource
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import kotlinx.coroutines.runBlocking

class StorageMediaDataSource(
    private val repository: FileRepository,
    private val path: String,
    private val storageType: StorageType,
    private val serverId: Long?,
    private val size: Long,
) : MediaDataSource() {

    @Volatile
    private var isClosed = false
    private var bufferStart: Long = -1L
    private var bufferData: ByteArray? = null

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
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

        val currentBuffer = if (!isClosed) bufferData else null
        val localOffset = if (currentBuffer != null) (position - bufferStart).toInt() else 0
        val available = if (currentBuffer != null) (currentBuffer.size - localOffset).coerceAtLeast(0) else 0
        val actualRead = bytesToRead.coerceAtMost(available)

        return if (currentBuffer != null && actualRead > 0) {
            System.arraycopy(currentBuffer, localOffset, buffer, offset, actualRead)
            actualRead
        } else {
            EOF
        }
    }

    private fun isPositionInBuffer(position: Long, requestedSize: Int): Boolean {
        val currentBuffer = bufferData ?: return false
        val bufferEnd = bufferStart + currentBuffer.size
        return position >= bufferStart && (position + requestedSize) <= bufferEnd
    }

    private fun fetchBufferChunk(position: Long) {
        if (isClosed) return
        val readSize = CHUNK_SIZE.toLong().coerceAtMost(size - position).toInt()
        if (readSize <= 0) return

        bufferStart = position
        bufferData = try {
            runBlocking {
                repository.readFile(path, position, readSize, storageType, serverId)
            }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    override fun getSize(): Long = size

    @Synchronized
    override fun close() {
        isClosed = true
        bufferData = null
        bufferStart = -1L
    }

    companion object {
        private const val EOF = -1
        private const val CHUNK_SIZE = 512 * 1024
    }
}
