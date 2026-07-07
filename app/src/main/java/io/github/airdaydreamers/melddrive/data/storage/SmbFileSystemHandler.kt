package io.github.airdaydreamers.melddrive.data.storage

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo0
import com.rapid7.client.dcerpc.transport.RPCTransport
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumSet

class SmbFileSystemHandler(private val server: RemoteServer) : StorageSource {

    private val client = SMBClient()

    private suspend fun <T> useSession(block: suspend (Session) -> T): T = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var session: Session? = null
        try {
            connection = client.connect(server.host, server.port)
            val authContext = if (server.isAnonymous) {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(server.username ?: "", (server.password ?: "").toCharArray(), null)
            }
            session = connection.authenticate(authContext)
            block(session)
        } finally {
            session?.close()
            connection?.close()
        }
    }

    override suspend fun listFiles(path: String): List<FileItem> = useSession { session ->
        if (path.isEmpty()) {
            val transport: RPCTransport = SMBTransportFactories.SRVSVC.getTransport(session)
            val serverService = ServerService(transport)
            val shares: List<NetShareInfo0> = serverService.shares0
            shares.filter {
                !it.netName.endsWith("$") && it.netName != "IPC$"
            }.map { share ->
                FileItem(
                    path = share.netName,
                    name = share.netName,
                    isDirectory = true,
                    storageType = StorageType.SMB
                )
            }
        } else {
            val parts = path.split("/", limit = 2)
            val shareName = parts[0]
            val relativePath = if (parts.size > 1) parts[1] else ""

            (session.connectShare(shareName) as DiskShare).use { share ->
                share.list(relativePath).map { info ->
                    val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                    FileItem(
                        path = "$shareName/${if (relativePath.isEmpty()) "" else "$relativePath/"}${info.fileName}",
                        name = info.fileName,
                        isDirectory = isDir,
                        size = if (isDir) 0 else info.endOfFile,
                        lastModified = info.changeTime.toEpochMillis(),
                        storageType = StorageType.SMB
                    )
                }.filter { it.name != "." && it.name != ".." }
            }
        }
    }

    override suspend fun deleteFile(path: String): Boolean = useSession { session ->
        val parts = path.split("/", limit = 2)
        if (parts.size < 2) return@useSession false
        val shareName = parts[0]
        val relativePath = parts[1]

        (session.connectShare(shareName) as DiskShare).use { share ->
            if (share.folderExists(relativePath)) {
                share.rmdir(relativePath, true)
                true
            } else if (share.fileExists(relativePath)) {
                share.rm(relativePath)
                true
            } else {
                false
            }
        }
    }

    override suspend fun renameFile(path: String, newName: String): Boolean = useSession { session ->
        val parts = path.split("/", limit = 2)
        if (parts.size < 2) return@useSession false
        val shareName = parts[0]
        val relativePath = parts[1]

        val parentPath = relativePath.substringBeforeLast("/", "")
        val targetPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"

        (session.connectShare(shareName) as DiskShare).use { share ->
            share.open(relativePath, EnumSet.of(com.hierynomus.msdtyp.AccessMask.GENERIC_ALL), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { entry ->
                entry.rename(targetPath)
            }
            true
        }
    }

    override suspend fun createFolder(parentPath: String, name: String): Boolean = useSession { session ->
        val parts = parentPath.split("/", limit = 2)
        if (parts.isEmpty()) return@useSession false
        val shareName = parts[0]
        val relativePath = if (parts.size > 1) parts[1] else ""

        val newFolderPath = if (relativePath.isEmpty()) name else "$relativePath/$name"

        (session.connectShare(shareName) as DiskShare).use { share ->
            share.mkdir(newFolderPath)
            true
        }
    }


    override suspend fun getFileSize(path: String): Long = useSession { session ->
        val parts = path.split("/", limit = 2)
        if (parts.size < 2) return@useSession 0L
        val shareName = parts[0]
        val relativePath = parts[1]

        (session.connectShare(shareName) as DiskShare).use { share ->
            share.getFileInformation(relativePath).standardInformation.endOfFile
        }
    }

    override suspend fun readFile(path: String, offset: Long, length: Int): ByteArray = useSession { session ->
        val parts = path.split("/", limit = 2)
        if (parts.size < 2) return@useSession ByteArray(0)
        val shareName = parts[0]
        val relativePath = parts[1]

        (session.connectShare(shareName) as DiskShare).use { share ->
            share.openFile(
                relativePath,
                EnumSet.of(com.hierynomus.msdtyp.AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            ).use { file ->
                val buffer = ByteArray(length)
                val bytesRead = file.read(buffer, offset, 0, length)
                if (bytesRead <= 0) return@useSession ByteArray(0)
                if (bytesRead == length) {
                    buffer
                } else {
                    buffer.copyOf(bytesRead)
                }
            }
        }
    }
}
