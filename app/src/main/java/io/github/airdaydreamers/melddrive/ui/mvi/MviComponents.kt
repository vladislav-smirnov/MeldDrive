package io.github.airdaydreamers.melddrive.ui.mvi

import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import io.github.airdaydreamers.melddrive.data.model.StorageType

data class FileManagerState(
    val currentPath: String,
    val currentStorageType: StorageType = StorageType.LOCAL,
    val currentServerId: Long? = null,
    val files: List<FileItem> = emptyList(),
    val sidebarItems: List<SidebarItem> = emptyList(),
    val isGridView: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val errorMessage: String? = null
)

sealed interface FileManagerIntent {
    data class NavigateTo(val path: String, val storageType: StorageType, val serverId: Long? = null) : FileManagerIntent
    data object NavigateUp : FileManagerIntent
    data class OpenFile(val fileItem: FileItem) : FileManagerIntent
    data class ToggleViewMode(val isGridView: Boolean) : FileManagerIntent
    data class Search(val query: String) : FileManagerIntent
    data class SetSearchActive(val isActive: Boolean) : FileManagerIntent
    data class SelectFile(val path: String) : FileManagerIntent
    data class DeleteFiles(val paths: Set<String>) : FileManagerIntent
    data class RenameFile(val path: String, val newName: String) : FileManagerIntent
    data class CreateFolder(val name: String) : FileManagerIntent
    data object Refresh : FileManagerIntent
    data object NavigateToAddStorage : FileManagerIntent
    data class DeleteRemoteServer(val serverId: Long) : FileManagerIntent
    data object NavigateToSettings : FileManagerIntent
}

sealed interface FileManagerEffect {
    data class ShowToast(val message: String) : FileManagerEffect
    data class OpenFileExternally(val fileItem: FileItem, val serverId: Long? = null) : FileManagerEffect
    data object NavigateToAddStorage : FileManagerEffect
    data object NavigateToSettings : FileManagerEffect
}
