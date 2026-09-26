package io.github.airdaydreamers.melddrive.data.storage

import android.media.MediaDataSource
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private var currentChunk: BufferChunk? = null

    private val fetchJob = Job()

    @Suppress("ReturnCount")
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

        val chunk = currentChunk ?: return EOF

        val localOffset = (position - chunk.start).toInt()
        if (localOffset < 0 || localOffset >= chunk.data.size) return EOF

        val available = (chunk.data.size - localOffset).coerceAtLeast(0)
        val actualRead = bytesToRead.coerceAtMost(available)

        return if (actualRead > 0) {
            System.arraycopy(chunk.data, localOffset, buffer, offset, actualRead)
            actualRead
        } else {
            EOF
        }
    }

    private fun isPositionInBuffer(position: Long, requestedSize: Int): Boolean {
        val chunk = currentChunk ?: return false
        val bufferEnd = chunk.start + chunk.data.size
        return position >= chunk.start && (position + requestedSize) <= bufferEnd
    }

    @Suppress("TooGenericExceptionCaught")
    private fun fetchBufferChunk(position: Long) {
        if (isClosed) return
        val readSize = CHUNK_SIZE.toLong().coerceAtMost(size - position).toInt()
        if (readSize <= 0) return

        Timber.d("StorageMediaDataSource: Fetching buffer chunk path=%s, position=%d, readSize=%d", path, position, readSize)
        val chunkData = try {
            runBlocking(fetchJob) {
                repository.readFile(path, position, readSize, storageType, serverId)
            }
        } catch (_: CancellationException) {
            Timber.d("StorageMediaDataSource: Fetch cancelled path=%s at position=%d", path, position)
            null
        } catch (_: InterruptedException) {
            Timber.d("StorageMediaDataSource: Thread interrupted path=%s at position=%d", path, position)
            null
        } catch (e: Exception) {
            Timber.e(e, "StorageMediaDataSource: Failed to fetch buffer chunk path=%s at position=%d", path, position)
            null
        }

        if (chunkData != null) {
            synchronized(this) {
                if (!isClosed) {
                    currentChunk = BufferChunk(position, chunkData)
                }
            }
        }
    }

    override fun getSize(): Long = size

    override fun close() {
        Timber.d("StorageMediaDataSource: Closing datasource for path=%s", path)
        isClosed = true
        fetchJob.cancel()
        synchronized(this) {
            currentChunk = null
        }
    }

    private data class BufferChunk(val start: Long, val data: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BufferChunk

            if (start != other.start) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = start.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    companion object {
        private const val EOF = -1
        private const val CHUNK_SIZE = 512 * 1024
    }
}
