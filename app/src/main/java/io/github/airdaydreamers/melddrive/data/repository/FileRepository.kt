package io.github.airdaydreamers.melddrive.data.repository

import io.github.airdaydreamers.melddrive.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class FileRepository {

    suspend fun listFiles(path: Path): List<FileItem> = withContext(Dispatchers.IO) {
        if (path.isDirectory()) {
            path.listDirectoryEntries().map { FileItem(it) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else {
            emptyList()
        }
    }

    suspend fun deleteFiles(paths: Set<Path>) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            if (path.isDirectory()) {
                path.toFile().deleteRecursively()
            } else {
                path.deleteIfExists()
            }
        }
    }

    suspend fun renameFile(path: Path, newName: String) = withContext(Dispatchers.IO) {
        val target = path.resolveSibling(newName)
        Files.move(path, target, StandardCopyOption.REPLACE_EXISTING)
    }

    suspend fun createFolder(parent: Path, name: String) = withContext(Dispatchers.IO) {
        val newFolder = parent.resolve(name)
        if (!newFolder.exists()) {
            Files.createDirectory(newFolder)
        }
    }

    suspend fun moveFiles(paths: Set<Path>, targetDirectory: Path) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            val target = targetDirectory.resolve(path.fileName)
            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
