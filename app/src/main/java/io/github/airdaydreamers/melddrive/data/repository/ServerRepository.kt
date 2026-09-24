package io.github.airdaydreamers.melddrive.data.repository

import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(private val remoteServerDao: RemoteServerDao, private val credentialStorage: CredentialStorage) {
    fun getRemoteServers(): Flow<List<RemoteServer>> = remoteServerDao.getAllServers()

    suspend fun addRemoteServer(server: RemoteServer, password: String?) = withContext(Dispatchers.IO) {
        Timber.i("ServerRepository: Adding remote server displayName=%s, host=%s", server.displayName, server.host)
        val id = remoteServerDao.insertServer(server)
        credentialStorage.saveCredentials(id, server.username, password)
    }

    suspend fun deleteRemoteServer(server: RemoteServer) = withContext(Dispatchers.IO) {
        Timber.i("ServerRepository: Deleting remote server id=%d, displayName=%s", server.id, server.displayName)
        remoteServerDao.deleteServer(server)
        credentialStorage.removeCredentials(server.id)
    }
}
