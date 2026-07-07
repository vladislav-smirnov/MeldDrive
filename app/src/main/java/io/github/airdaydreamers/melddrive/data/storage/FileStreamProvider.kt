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
import io.github.airdaydreamers.melddrive.data.db.AppDatabase
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.security.SecurityManager
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

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

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? {
        val path = uri.path?.substringAfterLast(".", "")
        return if (path.isNullOrEmpty()) "*/*" else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(path)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val segments = uri.pathSegments
        if (segments.size < 3) throw FileNotFoundException("Invalid URI: $uri")

        val storageType = StorageType.valueOf(segments[0])
        val serverId = segments[1].toLong().let { if (it == -1L) null else it }
        val filePath = segments.subList(2, segments.size).joinToString("/")

        val fileSize = runBlocking {
            repository.getFileSize(filePath, storageType, serverId)
        }

        val storageManager = context?.getSystemService(StorageManager::class.java)
            ?: throw FileNotFoundException("StorageManager not found")

        return try {
            storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.parseMode(mode),
                FileStreamCallback(repository, filePath, storageType, serverId, fileSize),
                handler
            )
        } catch (e: Exception) {
            throw FileNotFoundException("Failed to open proxy file descriptor: ${e.message}")
        }
    }

    private class FileStreamCallback(
        private val repository: FileRepository,
        private val path: String,
        private val storageType: StorageType,
        private val serverId: Long?,
        private val size: Long
    ) : ProxyFileDescriptorCallback() {

        private var buffer: ByteArray? = null
        private var bufferOffset: Long = -1L
        private val bufferSize = 1024 * 1024 // 1MB buffer

        override fun onGetSize(): Long = size

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            if (size <= 0) return 0

            return try {
                val currentBuffer = buffer
                if (currentBuffer != null && offset >= bufferOffset && offset + size <= bufferOffset + currentBuffer.size) {
                    // Data is in buffer
                    val startInBuf = (offset - bufferOffset).toInt()
                    System.arraycopy(currentBuffer, startInBuf, data, 0, size)
                    size
                } else if (size >= bufferSize) {
                    // Request is larger than buffer, read directly
                    val bytes = runBlocking {
                        repository.readFile(path, offset, size, storageType, serverId)
                    }
                    val copySize = Math.min(size, bytes.size)
                    System.arraycopy(bytes, 0, data, 0, copySize)
                    copySize
                } else {
                    // Read into buffer
                    val fetched = runBlocking {
                        repository.readFile(path, offset, bufferSize, storageType, serverId)
                    }
                    if (fetched.isEmpty()) return 0

                    buffer = fetched
                    bufferOffset = offset

                    val copySize = Math.min(size, fetched.size)
                    System.arraycopy(fetched, 0, data, 0, copySize)
                    copySize
                }
            } catch (e: Exception) {
                throw ErrnoException("onRead", OsConstants.EIO)
            }
        }

        override fun onRelease() {
            buffer = null
        }
    }

    companion object {
        const val AUTHORITY = "io.github.airdaydreamers.melddrive.filestream"

        fun buildUri(storageType: StorageType, serverId: Long?, path: String): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(storageType.name)
                .appendPath((serverId ?: -1L).toString())
                .appendEncodedPath(path)
                .build()
        }
    }
}
