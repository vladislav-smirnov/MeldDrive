package io.github.airdaydreamers.melddrive.data.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.IOException

class FileStreamProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FileStreamProviderEntryPoint {
        fun fileRepository(): FileRepository
        fun settingsManager(): SettingsManager
    }

    private lateinit var fileRepository: FileRepository
    private lateinit var fileSettingsManager: SettingsManager
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate(): Boolean {
        handlerThread = HandlerThread("FileStreamProviderThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        return true
    }

    private fun ensureDependencies(): Boolean {
        if (!::fileRepository.isInitialized) {
            val ctx = context?.applicationContext ?: return false
            val entryPoint = EntryPointAccessors.fromApplication(ctx, FileStreamProviderEntryPoint::class.java)
            fileRepository = entryPoint.fileRepository()
            fileSettingsManager = entryPoint.settingsManager()
        }
        return ::fileRepository.isInitialized
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val uriInfo = parseUri(uri) ?: return null

        val fileSize = if (ensureDependencies()) {
            try {
                runBlocking {
                    fileRepository.getFileSize(uriInfo.filePath, uriInfo.storageType, uriInfo.serverId)
                }
            } catch (_: Exception) {
                -1L
            }
        } else {
            -1L
        }

        val cols = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
        )

        val cursor = MatrixCursor(cols)
        val row = cursor.newRow()

        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(uriInfo.fileName)
                OpenableColumns.SIZE -> row.add(fileSize)
                MediaStore.MediaColumns.DATA -> row.add(null)
                MediaStore.MediaColumns.MIME_TYPE -> row.add(getType(uri))
                else -> row.add(null)
            }
        }

        return cursor
    }

    override fun getType(uri: Uri): String? {
        val path = uri.path?.substringAfterLast(".", "")
        return if (path.isNullOrEmpty()) "*/*" else MimeTypeMap.getSingleton().getMimeTypeFromExtension(path)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (!ensureDependencies()) {
            throw FileNotFoundException("Dependencies not initialized")
        }

        val uriInfo = parseUri(uri) ?: throw FileNotFoundException("Invalid URI: $uri")

        val bufferingEnabled = runBlocking { fileSettingsManager.bufferingEnabled.first() }
        val bufferSizeMb = runBlocking { fileSettingsManager.bufferSizeMb.first() }

        val fileSize = runBlocking {
            fileRepository.getFileSize(uriInfo.filePath, uriInfo.storageType, uriInfo.serverId)
        }

        val storageManager = context?.getSystemService(StorageManager::class.java)
            ?: throw FileNotFoundException("StorageManager not found")

        return try {
            storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.parseMode(mode),
                FileStreamCallback(
                    repository = fileRepository,
                    path = uriInfo.filePath,
                    storageType = uriInfo.storageType,
                    serverId = uriInfo.serverId,
                    size = fileSize,
                    bufferingEnabled = bufferingEnabled,
                    bufferSizeMb = bufferSizeMb,
                ),
                handler,
            )
        } catch (_: IOException) {
            throw FileNotFoundException("Failed to open proxy file descriptor")
        }
    }

    private fun parseUri(uri: Uri): StreamUriInfo? {
        val segments = uri.pathSegments
        if (segments.size < MIN_URI_SEGMENTS) return null

        val storageType = StorageType.entries.firstOrNull { it.name == segments[SEGMENT_STORAGE_TYPE] }
        val serverId = segments.getOrNull(SEGMENT_SERVER_ID)?.toLongOrNull()?.let { if (it == -1L) null else it }
        val filePath = segments.subList(PATH_START_INDEX, segments.size).joinToString("/")

        return storageType?.let { StreamUriInfo(it, serverId, filePath) }
    }

    private data class StreamUriInfo(val storageType: StorageType, val serverId: Long?, val filePath: String) {
        val fileName: String get() = filePath.substringAfterLast('/')
    }

    companion object {
        const val AUTHORITY = "io.github.airdaydreamers.melddrive.filestream"
        private const val MIN_URI_SEGMENTS = 3
        private const val SEGMENT_STORAGE_TYPE = 0
        private const val SEGMENT_SERVER_ID = 1
        private const val PATH_START_INDEX = 2

        fun buildUri(storageType: StorageType, serverId: Long?, path: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(storageType.name)
            .appendPath((serverId ?: -1L).toString())
            .appendEncodedPath(path)
            .build()
    }
}
