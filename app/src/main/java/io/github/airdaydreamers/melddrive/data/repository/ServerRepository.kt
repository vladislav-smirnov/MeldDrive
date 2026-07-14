package io.github.airdaydreamers.melddrive.data.repository

import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(private val remoteServerDao: RemoteServerDao, private val credentialStorage: CredentialStorage) {
    fun getRemoteServers(): Flow<List<RemoteServer>> = remoteServerDao.getAllServers()

    suspend fun addRemoteServer(server: RemoteServer, password: String?) = withContext(Dispatchers.IO) {
        val id = remoteServerDao.insertServer(server)
        credentialStorage.saveCredentials(id, server.username, password)
    }

    suspend fun deleteRemoteServer(server: RemoteServer) = withContext(Dispatchers.IO) {
        remoteServerDao.deleteServer(server)
        credentialStorage.removeCredentials(server.id)
    }
}
