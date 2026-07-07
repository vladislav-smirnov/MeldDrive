package io.github.airdaydreamers.melddrive.data.storage

import io.github.airdaydreamers.melddrive.data.model.FileItem

interface StorageSource {
    suspend fun listFiles(path: String): List<FileItem>
    suspend fun deleteFile(path: String): Boolean
    suspend fun renameFile(path: String, newName: String): Boolean
    suspend fun createFolder(parentPath: String, name: String): Boolean
    suspend fun getFileSize(path: String): Long
    suspend fun readFile(path: String, offset: Long, length: Int): ByteArray
    suspend fun searchFiles(path: String, query: String): List<FileItem>
}
