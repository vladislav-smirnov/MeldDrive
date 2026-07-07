package io.github.airdaydreamers.melddrive.ui.mvi

import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import java.nio.file.Path

data class FileManagerState(
    val currentPath: Path,
    val files: List<FileItem> = emptyList(),
    val sidebarItems: List<SidebarItem> = emptyList(),
    val isGridView: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedFiles: Set<Path> = emptySet(),
    val errorMessage: String? = null
)

sealed interface FileManagerIntent {
    data class NavigateTo(val path: Path) : FileManagerIntent
    data object NavigateUp : FileManagerIntent
    data class OpenFile(val fileItem: FileItem) : FileManagerIntent
    data class ToggleViewMode(val isGridView: Boolean) : FileManagerIntent
    data class Search(val query: String) : FileManagerIntent
    data class SelectFile(val path: Path) : FileManagerIntent
    data class DeleteFiles(val paths: Set<Path>) : FileManagerIntent
    data class RenameFile(val path: Path, val newName: String) : FileManagerIntent
    data class CreateFolder(val name: String) : FileManagerIntent
    data object Refresh : FileManagerIntent
}

sealed interface FileManagerEffect {
    data class ShowToast(val message: String) : FileManagerEffect
    data class OpenFileExternally(val fileItem: FileItem) : FileManagerEffect
}
