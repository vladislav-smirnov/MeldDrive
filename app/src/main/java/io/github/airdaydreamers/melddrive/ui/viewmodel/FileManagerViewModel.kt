package io.github.airdaydreamers.melddrive.ui.viewmodel

import android.os.Environment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerEffect
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerIntent
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths

class FileManagerViewModel(
    private val repository: FileRepository = FileRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(
        FileManagerState(
            currentPath = Paths.get(Environment.getExternalStorageDirectory().absolutePath),
            sidebarItems = getInitialSidebarItems()
        )
    )
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    private val _effect = Channel<FileManagerEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        loadFiles(_state.value.currentPath)
    }

    fun onIntent(intent: FileManagerIntent) {
        when (intent) {
            is FileManagerIntent.NavigateTo -> {
                _state.update { it.copy(currentPath = intent.path, selectedFiles = emptySet()) }
                loadFiles(intent.path)
            }
            FileManagerIntent.NavigateUp -> {
                _state.value.currentPath.parent?.let { parent ->
                    onIntent(FileManagerIntent.NavigateTo(parent))
                }
            }
            is FileManagerIntent.OpenFile -> {
                if (intent.fileItem.isDirectory) {
                    onIntent(FileManagerIntent.NavigateTo(intent.fileItem.path))
                } else {
                    viewModelScope.launch {
                        _effect.send(FileManagerEffect.OpenFileExternally(intent.fileItem))
                    }
                }
            }
            is FileManagerIntent.ToggleViewMode -> {
                _state.update { it.copy(isGridView = intent.isGridView) }
            }
            is FileManagerIntent.Search -> {
                _state.update { it.copy(searchQuery = intent.query) }
            }
            is FileManagerIntent.SelectFile -> {
                _state.update {
                    val newSelection = if (it.selectedFiles.contains(intent.path)) {
                        it.selectedFiles - intent.path
                    } else {
                        it.selectedFiles + intent.path
                    }
                    it.copy(selectedFiles = newSelection)
                }
            }
            is FileManagerIntent.DeleteFiles -> deleteFiles(intent.paths)
            is FileManagerIntent.RenameFile -> renameFile(intent.path, intent.newName)
            is FileManagerIntent.CreateFolder -> createFolder(intent.name)
            FileManagerIntent.Refresh -> loadFiles(_state.value.currentPath)
        }
    }

    private fun loadFiles(path: Path) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val files = repository.listFiles(path)
                _state.update { it.copy(files = files, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun deleteFiles(paths: Set<Path>) {
        viewModelScope.launch {
            try {
                repository.deleteFiles(paths)
                _state.update { it.copy(selectedFiles = emptySet()) }
                loadFiles(_state.value.currentPath)
                _effect.send(FileManagerEffect.ShowToast("Files deleted"))
            } catch (e: Exception) {
                _effect.send(FileManagerEffect.ShowToast("Error deleting files: ${e.message}"))
            }
        }
    }

    private fun renameFile(path: Path, newName: String) {
        viewModelScope.launch {
            try {
                repository.renameFile(path, newName)
                loadFiles(_state.value.currentPath)
                _effect.send(FileManagerEffect.ShowToast("File renamed"))
            } catch (e: Exception) {
                _effect.send(FileManagerEffect.ShowToast("Error renaming file: ${e.message}"))
            }
        }
    }

    private fun createFolder(name: String) {
        viewModelScope.launch {
            try {
                repository.createFolder(_state.value.currentPath, name)
                loadFiles(_state.value.currentPath)
                _effect.send(FileManagerEffect.ShowToast("Folder created"))
            } catch (e: Exception) {
                _effect.send(FileManagerEffect.ShowToast("Error creating folder: ${e.message}"))
            }
        }
    }

    private fun getInitialSidebarItems(): List<SidebarItem> {
        val root = Environment.getExternalStorageDirectory()
        return listOf(
            SidebarItem("home", "Home", Paths.get(root.absolutePath), SidebarItemType.SYSTEM_FOLDER, Icons.Default.Home),
            SidebarItem("downloads", "Downloads", Paths.get(root.absolutePath, Environment.DIRECTORY_DOWNLOADS), SidebarItemType.SYSTEM_FOLDER, Icons.Default.Download),
            SidebarItem("dcim", "Photos", Paths.get(root.absolutePath, Environment.DIRECTORY_DCIM), SidebarItemType.SYSTEM_FOLDER, Icons.Default.Photo),
            SidebarItem("movies", "Movies", Paths.get(root.absolutePath, Environment.DIRECTORY_MOVIES), SidebarItemType.SYSTEM_FOLDER, Icons.Default.Movie),
            SidebarItem("music", "Music", Paths.get(root.absolutePath, Environment.DIRECTORY_MUSIC), SidebarItemType.SYSTEM_FOLDER, Icons.Default.MusicNote),
        )
    }
}
