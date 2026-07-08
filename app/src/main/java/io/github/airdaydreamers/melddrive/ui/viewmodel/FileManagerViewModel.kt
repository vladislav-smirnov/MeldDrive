package io.github.airdaydreamers.melddrive.ui.viewmodel

import android.os.Environment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Storage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerEffect
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerIntent
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class FileManagerViewModel(private val repository: FileRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        FileManagerState(
            currentPath = Environment.getExternalStorageDirectory().absolutePath,
        ),
    )
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    private val _effect = Channel<FileManagerEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        repository.getRemoteServers()
            .onEach { servers ->
                _state.update { it.copy(sidebarItems = getSidebarItems(servers)) }
            }.launchIn(viewModelScope)

        loadFiles(_state.value.currentPath, _state.value.currentStorageType, _state.value.currentServerId)
    }

    fun onIntent(intent: FileManagerIntent) {
        when (intent) {
            is FileManagerIntent.NavigateTo -> {
                _state.update {
                    it.copy(
                        currentPath = intent.path,
                        currentStorageType = intent.storageType,
                        currentServerId = intent.serverId,
                        selectedFiles = emptySet(),
                    )
                }
                loadFiles(intent.path, intent.storageType, intent.serverId)
            }

            FileManagerIntent.NavigateUp -> {
                val currentPath = _state.value.currentPath
                if (_state.value.currentStorageType == StorageType.LOCAL) {
                    val parent = File(currentPath).parent
                    if (parent != null) {
                        onIntent(FileManagerIntent.NavigateTo(parent, StorageType.LOCAL))
                    }
                } else if (_state.value.currentStorageType == StorageType.SMB) {
                    if (currentPath.isNotEmpty()) {
                        val parent = if (currentPath.contains("/")) {
                            currentPath.substringBeforeLast("/")
                        } else {
                            ""
                        }
                        onIntent(FileManagerIntent.NavigateTo(parent, StorageType.SMB, _state.value.currentServerId))
                    }
                }
            }

            is FileManagerIntent.OpenFile -> {
                if (intent.fileItem.isDirectory) {
                    onIntent(FileManagerIntent.NavigateTo(intent.fileItem.path, intent.fileItem.storageType, _state.value.currentServerId))
                } else {
                    viewModelScope.launch {
                        _effect.send(FileManagerEffect.OpenFileExternally(intent.fileItem, _state.value.currentServerId))
                    }
                }
            }

            is FileManagerIntent.ToggleViewMode -> {
                _state.update { it.copy(isGridView = intent.isGridView) }
            }

            is FileManagerIntent.Search -> {
                _state.update { it.copy(searchQuery = intent.query) }
                if (intent.query.length >= 3) {
                    searchFiles(_state.value.currentPath, intent.query, _state.value.currentStorageType, _state.value.currentServerId)
                } else {
                    // Fallback to current folder listing if query is too short
                    loadFiles(_state.value.currentPath, _state.value.currentStorageType, _state.value.currentServerId)
                }
            }

            is FileManagerIntent.SetSearchActive -> {
                val wasActive = _state.value.isSearchActive
                _state.update {
                    it.copy(
                        isSearchActive = intent.isActive,
                        searchQuery = if (!intent.isActive) "" else it.searchQuery,
                    )
                }
                if (wasActive && !intent.isActive) {
                    // Refresh current folder when search is closed
                    loadFiles(_state.value.currentPath, _state.value.currentStorageType, _state.value.currentServerId)
                }
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

            FileManagerIntent.Refresh -> loadFiles(_state.value.currentPath, _state.value.currentStorageType, _state.value.currentServerId)

            FileManagerIntent.NavigateToAddStorage -> {
                viewModelScope.launch {
                    _effect.send(FileManagerEffect.NavigateToAddStorage)
                }
            }

            is FileManagerIntent.DeleteRemoteServer -> deleteRemoteServer(intent.serverId)

            FileManagerIntent.NavigateToSettings -> {
                viewModelScope.launch {
                    _effect.send(FileManagerEffect.NavigateToSettings)
                }
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun searchFiles(path: String, query: String, storageType: StorageType, serverId: Long?) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, files = emptyList()) }
            try {
                val files = repository.searchFiles(path, query, storageType, serverId)
                _state.update { it.copy(files = files, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                _effect.send(FileManagerEffect.ShowToast("Search error: ${e.message}"))
            }
        }
    }

    private fun deleteRemoteServer(serverId: Long) {
        viewModelScope.launch {
            try {
                val servers = repository.getRemoteServers().first()
                val serverToDelete = servers.find { it.id == serverId }
                if (serverToDelete != null) {
                    repository.deleteRemoteServer(serverToDelete)
                    if (_state.value.currentServerId == serverId) {
                        onIntent(FileManagerIntent.NavigateTo(Environment.getExternalStorageDirectory().absolutePath, StorageType.LOCAL))
                    }
                    _effect.send(FileManagerEffect.ShowToast("Server removed"))
                }
            } catch (e: Exception) {
                _effect.send(FileManagerEffect.ShowToast("Error removing server: ${e.message}"))
            }
        }
    }

    private fun loadFiles(path: String, storageType: StorageType, serverId: Long?) {
        android.util.Log.d("FileManagerViewModel", "loadFiles: path='$path', storageType=$storageType, serverId=$serverId")
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, files = emptyList(), errorMessage = null) }
            try {
                val files = repository.listFiles(path, storageType, serverId)
                android.util.Log.d("FileManagerViewModel", "loadFiles success: loaded ${files.size} files")
                _state.update { it.copy(files = files, isLoading = false) }
            } catch (e: Exception) {
                android.util.Log.e("FileManagerViewModel", "loadFiles error: ${e.message}", e)
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
                _effect.send(FileManagerEffect.ShowToast("Error: ${e.message}"))
            }
        }
    }

    private fun deleteFiles(paths: Set<String>) {
        viewModelScope.launch {
            try {
                paths.forEach { path ->
                    repository.deleteFile(path, _state.value.currentStorageType, _state.value.currentServerId)
                }
                _state.update { it.copy(selectedFiles = emptySet()) }
                onIntent(FileManagerIntent.Refresh)
                _effect.send(FileManagerEffect.ShowToast("Files deleted"))
            } catch (e: Exception) {
                _effect.send(FileManagerEffect.ShowToast("Error deleting files: ${e.message}"))
            }
        }
    }

    private fun renameFile(path: String, newName: String) {
        viewModelScope.launch {
            try {
                repository.renameFile(path, newName, _state.value.currentStorageType, _state.value.currentServerId)
                onIntent(FileManagerIntent.Refresh)
                _effect.send(FileManagerEffect.ShowToast("File renamed"))
            } catch (e: Exception) {
                _effect.send(FileManagerEffect.ShowToast("Error renaming file: ${e.message}"))
            }
        }
    }

    private fun createFolder(name: String) {
        viewModelScope.launch {
            try {
                repository.createFolder(_state.value.currentPath, name, _state.value.currentStorageType, _state.value.currentServerId)
                onIntent(FileManagerIntent.Refresh)
                _effect.send(FileManagerEffect.ShowToast("Folder created"))
            } catch (e: Exception) {
                _effect.send(FileManagerEffect.ShowToast("Error creating folder: ${e.message}"))
            }
        }
    }

    private fun getSidebarItems(remoteServers: List<io.github.airdaydreamers.melddrive.data.db.RemoteServer>): List<SidebarItem> {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val items = mutableListOf(
            SidebarItem("home", "Home", root, SidebarItemType.SYSTEM_FOLDER, Icons.Default.Home),
            SidebarItem(
                "downloads",
                "Downloads",
                File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath,
                SidebarItemType.SYSTEM_FOLDER,
                Icons.Default.Download,
            ),
            SidebarItem("dcim", "Photos", File(root, Environment.DIRECTORY_DCIM).absolutePath, SidebarItemType.SYSTEM_FOLDER, Icons.Default.Photo),
            SidebarItem("movies", "Movies", File(root, Environment.DIRECTORY_MOVIES).absolutePath, SidebarItemType.SYSTEM_FOLDER, Icons.Default.Movie),
            SidebarItem("music", "Music", File(root, Environment.DIRECTORY_MUSIC).absolutePath, SidebarItemType.SYSTEM_FOLDER, Icons.Default.MusicNote),
        )

        remoteServers.forEach { server ->
            items.add(
                SidebarItem(
                    id = "remote_${server.id}",
                    title = server.displayName,
                    path = "", // Root of SMB
                    type = SidebarItemType.REMOTE_SERVER,
                    icon = Icons.Default.Storage,
                    serverId = server.id,
                ),
            )
        }

        items.add(
            SidebarItem("add_storage", "Add Storage", null, SidebarItemType.ADD_STORAGE, Icons.Default.Add),
        )

        return items
    }
}
