package io.github.airdaydreamers.melddrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.ui.components.*
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
    onNavigateToAddStorage: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FileManagerEffect.OpenFileExternally -> onOpenFile(effect)
                is FileManagerEffect.ShowToast -> onShowToast(effect.message)
                FileManagerEffect.NavigateToAddStorage -> onNavigateToAddStorage()
            }
        }
    }

    FileManagerContent(
        state = state,
        onIntent = viewModel::onIntent
    )
}

@Composable
fun FileManagerContent(
    state: FileManagerState,
    onIntent: (FileManagerIntent) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = state.currentPath.isNotEmpty()) {
        onIntent(FileManagerIntent.NavigateUp)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FileManagerDrawerContent(
                items = state.sidebarItems,
                currentPath = state.currentPath,
                onItemClick = { item ->
                    if (item.type == SidebarItemType.ADD_STORAGE) {
                        onIntent(FileManagerIntent.NavigateToAddStorage) // This was missing in intent previously but handled by a separate method, let's add it or keep it as is. Actually FileManagerViewModel has onAddStorageClick.
                    } else {
                        val storageType = when (item.type) {
                            SidebarItemType.REMOTE_SERVER -> StorageType.SMB
                            else -> StorageType.LOCAL
                        }
                        item.path?.let { onIntent(FileManagerIntent.NavigateTo(it, storageType, item.serverId)) }
                    }
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                FileManagerTopBar(
                    currentPath = state.currentPath,
                    isGridView = state.isGridView,
                    searchQuery = state.searchQuery,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNavigateTo = { onIntent(FileManagerIntent.NavigateTo(it, state.currentStorageType, state.currentServerId)) },
                    onToggleViewMode = { onIntent(FileManagerIntent.ToggleViewMode(it)) },
                    onSearchQueryChange = { onIntent(FileManagerIntent.Search(it)) }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                } else {
                    val filteredFiles = remember(state.files, state.searchQuery) {
                        if (state.searchQuery.isEmpty()) state.files
                        else state.files.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
                    }

                    if (state.isGridView) {
                        FileGrid(
                            files = filteredFiles,
                            selectedFiles = state.selectedFiles,
                            onFileClick = { onIntent(FileManagerIntent.OpenFile(it)) },
                            onFileLongClick = { onIntent(FileManagerIntent.SelectFile(it.path)) }
                        )
                    } else {
                        FileList(
                            files = filteredFiles,
                            selectedFiles = state.selectedFiles,
                            onFileClick = { onIntent(FileManagerIntent.OpenFile(it)) },
                            onFileLongClick = { onIntent(FileManagerIntent.SelectFile(it.path)) }
                        )
                    }
                }
            }
        }
    }
}
