package io.github.airdaydreamers.melddrive.data.repository

import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageException
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.storage.LocalFileSystemHandler
import io.github.airdaydreamers.melddrive.data.storage.SmbFileSystemHandler
import io.github.airdaydreamers.melddrive.data.storage.StorageSource
import io.github.airdaydreamers.melddrive.data.storage.webdav.WebDavFileSystemHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class FileRepository @Inject constructor(
    private val remoteServerDao: RemoteServerDao,
    private val credentialStorage: CredentialStorage,
    private val localHandler: LocalFileSystemHandler,
    private val smbHandlerFactory: SmbFileSystemHandler.Factory,
    private val webDavHandlerFactory: WebDavFileSystemHandler.Factory,
) {

    private val remoteHandlers = ConcurrentHashMap<Long, StorageSource>()

    fun clearHandler(serverId: Long) {
        remoteHandlers.remove(serverId)
    }

    private suspend fun getHandler(storageType: StorageType, serverId: Long?): StorageSource = withContext(Dispatchers.IO) {
        Timber.d("FileRepository: Getting handler for storageType=%s, serverId=%s", storageType, serverId)
        when (storageType) {
            StorageType.LOCAL -> localHandler

            StorageType.SMB -> {
                val id = serverId ?: throw StorageException("Server ID required for SMB")
                getOrCreateRemoteHandler(id) { smbHandlerFactory.create(it) }
            }

            StorageType.WEBDAV -> {
                val id = serverId ?: throw StorageException("Server ID required for WEBDAV")
                getOrCreateRemoteHandler(id) { webDavHandlerFactory.create(it) }
            }

            else -> throw UnsupportedOperationException("Storage type $storageType not supported yet")
        }
    }

    private suspend fun getOrCreateRemoteHandler(id: Long, createFactory: (RemoteServer) -> StorageSource): StorageSource = remoteHandlers.getOrPut(id) {
        val server = remoteServerDao.getServerById(id) ?: throw StorageException("Server not found")
        var username = credentialStorage.getUsername(id)
        var password = credentialStorage.getPassword(id)

        if (username == null && password == null && !server.isAnonymous) {
            username = server.username
            password = server.password
            if (username != null || password != null) {
                credentialStorage.saveCredentials(id, username, password)
            }
        }

        val serverWithCredentials = server.copy(username = username, password = password)
        createFactory(serverWithCredentials)
    }

    open suspend fun listFiles(path: String, storageType: StorageType, serverId: Long? = null): List<FileItem> {
        Timber.d("FileRepository: listFiles path=%s, storageType=%s, serverId=%s", path, storageType, serverId)
        return getHandler(storageType, serverId).listFiles(path)
    }

    suspend fun deleteFile(path: String, storageType: StorageType, serverId: Long?) {
        getHandler(storageType, serverId).deleteFile(path)
    }

    suspend fun renameFile(path: String, newName: String, storageType: StorageType, serverId: Long?) {
        getHandler(storageType, serverId).renameFile(path, newName)
    }

    suspend fun createFolder(parentPath: String, name: String, storageType: StorageType, serverId: Long?) {
        getHandler(storageType, serverId).createFolder(parentPath, name)
    }

    suspend fun getFileSize(path: String, storageType: StorageType, serverId: Long?): Long = getHandler(storageType, serverId).getFileSize(path)

    suspend fun readFile(path: String, offset: Long, length: Int, storageType: StorageType, serverId: Long?): ByteArray =
        getHandler(storageType, serverId).readFile(path, offset, length)

    suspend fun searchFiles(path: String, query: String, storageType: StorageType, serverId: Long?): List<FileItem> =
        getHandler(storageType, serverId).searchFiles(path, query)
}
