package io.github.airdaydreamers.melddrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.ui.components.FileGrid
import io.github.airdaydreamers.melddrive.ui.components.FileList
import io.github.airdaydreamers.melddrive.ui.components.FileManagerDrawerContent
import io.github.airdaydreamers.melddrive.ui.components.FileManagerTopBar
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerEffect
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerIntent
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerState
import io.github.airdaydreamers.melddrive.ui.viewmodel.FileManagerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onOpenFile: (FileManagerEffect.OpenFileExternally) -> Unit,
    onShowToast: (String) -> Unit,
    onNavigateToAddStorage: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FileManagerEffect.OpenFileExternally -> onOpenFile(effect)
                is FileManagerEffect.ShowToast -> onShowToast(effect.message)
                FileManagerEffect.NavigateToAddStorage -> onNavigateToAddStorage()
                FileManagerEffect.NavigateToSettings -> onNavigateToSettings()
            }
        }
    }

    var serverToDelete by remember { mutableStateOf<Long?>(null) }

    if (serverToDelete != null) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Remove Server") },
            text = { Text("Are you sure you want to remove this server?") },
            confirmButton = {
                TextButton(onClick = {
                    serverToDelete?.let { viewModel.onIntent(FileManagerIntent.DeleteRemoteServer(it)) }
                    serverToDelete = null
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    FileManagerContent(
        state = state,
        onIntent = viewModel::onIntent,
        onDeleteServer = { serverToDelete = it },
    )
}

@Composable
fun FileManagerContent(state: FileManagerState, onIntent: (FileManagerIntent) -> Unit, onDeleteServer: (Long) -> Unit = {}) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentServerName = remember(state.currentServerId, state.sidebarItems) {
        state.sidebarItems.find { (it.serverId == state.currentServerId) && (it.type == SidebarItemType.REMOTE_SERVER) }?.title
    }

    BackHandler(enabled = state.currentPath.isNotEmpty() || state.isSearchActive) {
        if (state.isSearchActive) {
            onIntent(FileManagerIntent.SetSearchActive(false))
        } else {
            onIntent(FileManagerIntent.NavigateUp)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FileManagerDrawerContent(
                items = state.sidebarItems,
                currentPath = state.currentPath,
                onDeleteServer = onDeleteServer,
                onItemClick = { item ->
                    handleDrawerItemClick(item, onIntent, scope, drawerState)
                },
            )
        },
    ) {
        FileManagerMainScaffold(
            state = state,
            currentServerName = currentServerName,
            onIntent = onIntent,
            onMenuClick = { scope.launch { drawerState.open() } },
        )
    }
}

private fun handleDrawerItemClick(
    item: io.github.airdaydreamers.melddrive.data.model.SidebarItem,
    onIntent: (FileManagerIntent) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    drawerState: androidx.compose.material3.DrawerState,
) {
    if (item.type == SidebarItemType.ADD_STORAGE) {
        onIntent(FileManagerIntent.NavigateToAddStorage)
    } else {
        val storageType = when (item.type) {
            SidebarItemType.REMOTE_SERVER -> StorageType.SMB
            else -> StorageType.LOCAL
        }
        item.path?.let { onIntent(FileManagerIntent.NavigateTo(it, storageType, item.serverId)) }
    }
    scope.launch { drawerState.close() }
}

@Composable
private fun FileManagerMainScaffold(state: FileManagerState, currentServerName: String?, onIntent: (FileManagerIntent) -> Unit, onMenuClick: () -> Unit) {
    Scaffold(
        topBar = {
            FileManagerTopBar(
                currentPath = state.currentPath,
                storageType = state.currentStorageType,
                serverName = currentServerName,
                isGridView = state.isGridView,
                searchQuery = state.searchQuery,
                isSearchActive = state.isSearchActive,
                onMenuClick = onMenuClick,
                onNavigateTo = { onIntent(FileManagerIntent.NavigateTo(it, state.currentStorageType, state.currentServerId)) },
                onToggleViewMode = { onIntent(FileManagerIntent.ToggleViewMode(it)) },
                onSearchQueryChange = { onIntent(FileManagerIntent.Search(it)) },
                onSearchActiveChange = { onIntent(FileManagerIntent.SetSearchActive(it)) },
                onSettingsClick = { onIntent(FileManagerIntent.NavigateToSettings) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            } else {
                FileListView(state, onIntent)
            }
        }
    }
}

@Composable
private fun FileListView(state: FileManagerState, onIntent: (FileManagerIntent) -> Unit) {
    val filteredFiles = remember(state.files, state.searchQuery) {
        if (state.searchQuery.isEmpty()) {
            state.files
        } else {
            state.files.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
        }
    }

    if (state.isGridView) {
        FileGrid(
            files = filteredFiles,
            selectedFiles = state.selectedFiles,
            onFileClick = { onIntent(FileManagerIntent.OpenFile(it)) },
            onFileLongClick = { onIntent(FileManagerIntent.SelectFile(it.path)) },
        )
    } else {
        FileList(
            files = filteredFiles,
            selectedFiles = state.selectedFiles,
            onFileClick = { onIntent(FileManagerIntent.OpenFile(it)) },
            onFileLongClick = { onIntent(FileManagerIntent.SelectFile(it.path)) },
        )
    }
}
