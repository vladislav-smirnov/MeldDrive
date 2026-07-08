package io.github.airdaydreamers.melddrive.data.storage

import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class LocalFileSystemHandler : StorageSource {
    override suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val nioPath = Paths.get(path)
        if (nioPath.isDirectory()) {
            nioPath.listDirectoryEntries().map {
                FileItem(
                    path = it.toString(),
                    name = it.name,
                    isDirectory = it.isDirectory(),
                    size = if (it.isDirectory()) {
                        0
                    } else {
                        try {
                            Files.size(it)
                        } catch (ignored: IOException) {
                            0
                        }
                    },
                    lastModified = try {
                        Files.getLastModifiedTime(it).toMillis()
                    } catch (ignored: IOException) {
                        0
                    },
                    storageType = StorageType.LOCAL,
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else {
            emptyList()
        }
    }

    override suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val nioPath = Paths.get(path)
        if (nioPath.isDirectory()) {
            nioPath.toFile().deleteRecursively()
        } else {
            nioPath.deleteIfExists()
        }
    }

    override suspend fun renameFile(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val nioPath = Paths.get(path)
        val target = nioPath.resolveSibling(newName)
        Files.move(nioPath, target)
        true
    }

    override suspend fun createFolder(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val parent = Paths.get(parentPath)
        val newFolder = parent.resolve(name)
        if (!newFolder.exists()) {
            Files.createDirectory(newFolder)
            true
        } else {
            false
        }
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        Files.size(Paths.get(path))
    }

    override suspend fun readFile(path: String, offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        Files.newByteChannel(Paths.get(path)).use { channel ->
            channel.position(offset)
            val buffer = java.nio.ByteBuffer.allocate(length)
            while (buffer.hasRemaining()) {
                val bytesRead = channel.read(buffer)
                if (bytesRead <= 0) break
            }
            val totalBytesRead = buffer.position()
            if (totalBytesRead <= 0) return@withContext ByteArray(0)
            if (totalBytesRead == length) {
                buffer.array()
            } else {
                buffer.array().copyOf(totalBytesRead)
            }
        }
    }

    override suspend fun searchFiles(path: String, query: String): List<FileItem> = withContext(Dispatchers.IO) {
        val root = File(path)
        val result = mutableListOf<FileItem>()
        root.walkTopDown().forEach { file ->
            if (file.name.contains(query, ignoreCase = true)) {
                result.add(
                    FileItem(
                        path = file.absolutePath,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        size = if (file.isDirectory) 0 else file.length(),
                        lastModified = file.lastModified(),
                        storageType = StorageType.LOCAL,
                    ),
                )
            }
        }
        result
    }
}
