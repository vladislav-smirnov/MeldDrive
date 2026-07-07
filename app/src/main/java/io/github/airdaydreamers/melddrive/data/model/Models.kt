package io.github.airdaydreamers.melddrive.data.model

import androidx.compose.ui.graphics.vector.ImageVector

data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val storageType: StorageType = StorageType.LOCAL
)

enum class StorageType {
    LOCAL, SMB, DLNA, WEBDAV
}

enum class SidebarItemType {
    SYSTEM_FOLDER, FAVORITE, TAG, REMOTE_SERVER, ADD_STORAGE
}

data class SidebarItem(
    val id: String,
    val title: String,
    val path: String?,
    val type: SidebarItemType,
    val icon: ImageVector,
    val serverId: Long? = null
)
