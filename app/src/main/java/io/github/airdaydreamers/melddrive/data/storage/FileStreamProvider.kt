package io.github.airdaydreamers.melddrive.data.storage

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
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

    private lateinit var repository: FileRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    override fun onCreate(): Boolean {
        val context = context ?: return false
        val entryPoint = EntryPointAccessors.fromApplication(context, FileStreamProviderEntryPoint::class.java)
        repository = entryPoint.fileRepository()
        settingsManager = entryPoint.settingsManager()
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
        } catch (_: IOException) {
            throw FileNotFoundException("Failed to open proxy file descriptor")
        }
    }

    companion object {
        const val AUTHORITY = "io.github.airdaydreamers.melddrive.filestream"
        private const val MIN_URI_SEGMENTS = 3

        fun buildUri(storageType: StorageType, serverId: Long?, path: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(storageType.name)
            .appendPath((serverId ?: -1L).toString())
            .appendEncodedPath(path)
            .build()
    }
}
