package io.github.airdaydreamers.melddrive.data.repository

import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.storage.LocalFileSystemHandler
import io.github.airdaydreamers.melddrive.data.storage.SmbFileSystemHandler
import io.github.airdaydreamers.melddrive.data.storage.StorageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FileRepository(private val remoteServerDao: RemoteServerDao, private val credentialStorage: CredentialStorage) {

    private val localHandler = LocalFileSystemHandler()
    private val smbHandlers = mutableMapOf<Long, SmbFileSystemHandler>()

    fun getRemoteServers(): Flow<List<RemoteServer>> = remoteServerDao.getAllServers()

    suspend fun addRemoteServer(server: RemoteServer, password: String?) = withContext(Dispatchers.IO) {
        val id = remoteServerDao.insertServer(server)
        credentialStorage.saveCredentials(id, server.username, password)
    }

    suspend fun deleteRemoteServer(server: RemoteServer) = withContext(Dispatchers.IO) {
        remoteServerDao.deleteServer(server)
        credentialStorage.removeCredentials(server.id)
        smbHandlers.remove(server.id)
    }

    private suspend fun getHandler(storageType: StorageType, serverId: Long?): StorageSource = withContext(Dispatchers.IO) {
        when (storageType) {
            StorageType.LOCAL -> localHandler

            StorageType.SMB -> {
                serverId?.let { id ->
                    smbHandlers.getOrPut(id) {
                        val server = remoteServerDao.getServerById(id) ?: throw Exception("Server not found")
                        var username = credentialStorage.getUsername(id)
                        var password = credentialStorage.getPassword(id)

                        // Migration/Fallback: if not in credentialStorage, use from DB and migrate
                        if (username == null && password == null && (!server.isAnonymous)) {
                            username = server.username
                            password = server.password
                            if (username != null || password != null) {
                                credentialStorage.saveCredentials(id, username, password)
                            }
                        }

                        val serverWithCredentials = server.copy(username = username, password = password)
                        SmbFileSystemHandler(serverWithCredentials)
                    }
                } ?: throw Exception("Server ID required for SMB")
            }

            else -> throw UnsupportedOperationException("Storage type $storageType not supported yet")
        }
    }

    suspend fun listFiles(path: String, storageType: StorageType, serverId: Long? = null): List<FileItem> = getHandler(storageType, serverId).listFiles(path)

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
