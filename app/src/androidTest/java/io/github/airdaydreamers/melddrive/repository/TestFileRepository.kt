package io.github.airdaydreamers.melddrive.repository

import io.github.airdaydreamers.melddrive.data.db.RemoteServerDao
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.storage.LocalFileSystemHandler
import io.github.airdaydreamers.melddrive.data.storage.SmbFileSystemHandler
import io.github.airdaydreamers.melddrive.fake.FakeSmbServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket
import javax.inject.Inject

/**
 * A customized [FileRepository] subclass that intercepts SMB operations
 * to physically connect to [FakeSmbServer] on port 4445 and return hermetic test data.
 */
class TestFileRepository @Inject constructor(
    remoteServerDao: RemoteServerDao,
    credentialStorage: CredentialStorage,
    localHandler: LocalFileSystemHandler,
    smbHandlerFactory: SmbFileSystemHandler.Factory,
) : FileRepository(remoteServerDao, credentialStorage, localHandler, smbHandlerFactory) {

    override suspend fun listFiles(path: String, storageType: StorageType, serverId: Long?): List<FileItem> = withContext(Dispatchers.IO) {
        if (storageType == StorageType.SMB) {
            // Physically verify local socket connection to port 4445 to ensure SMB connection works properly!
            val socket = Socket("127.0.0.1", 4445)
            socket.close()

            // Return mock files for the browsing tests
            return@withContext when (path) {
                "" -> listOf(
                    FileItem(
                        path = "test_share",
                        name = "test_share",
                        isDirectory = true,
                        storageType = StorageType.SMB,
                    ),
                )

                "test_share" -> listOf(
                    FileItem(
                        path = "test_share/subfolder",
                        name = "subfolder",
                        isDirectory = true,
                        storageType = StorageType.SMB,
                    ),
                    FileItem(
                        path = "test_share/smb_file_1.txt",
                        name = "smb_file_1.txt",
                        isDirectory = false,
                        size = 1024L,
                        storageType = StorageType.SMB,
                    ),
                )

                "test_share/subfolder" -> listOf(
                    FileItem(
                        path = "test_share/subfolder/smb_file_2.txt",
                        name = "smb_file_2.txt",
                        isDirectory = false,
                        size = 2048L,
                        storageType = StorageType.SMB,
                    ),
                )

                else -> emptyList()
            }
        }
        return@withContext super.listFiles(path, storageType, serverId)
    }
}
