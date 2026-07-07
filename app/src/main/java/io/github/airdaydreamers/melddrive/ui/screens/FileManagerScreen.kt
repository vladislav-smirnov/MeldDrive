package io.github.airdaydreamers.melddrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.ui.components.FileGrid
import io.github.airdaydreamers.melddrive.ui.components.FileList
import io.github.airdaydreamers.melddrive.ui.components.FileManagerDrawerContent
import io.github.airdaydreamers.melddrive.ui.components.FileManagerTopBar
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerEffect
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerIntent
import io.github.airdaydreamers.melddrive.ui.viewmodel.FileManagerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onOpenFile: (FileManagerEffect) -> Unit,
    onShowToast: (String) -> Unit,
    onNavigateToAddStorage: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FileManagerEffect.OpenFileExternally -> onOpenFile(effect)
                is FileManagerEffect.ShowToast -> onShowToast(effect.message)
                FileManagerEffect.NavigateToAddStorage -> onNavigateToAddStorage()
            }
        }
    }

    BackHandler(enabled = state.currentPath.isNotEmpty()) {
        viewModel.onIntent(FileManagerIntent.NavigateUp)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FileManagerDrawerContent(
                items = state.sidebarItems,
                currentPath = state.currentPath,
                onItemClick = { item ->
                    if (item.type == SidebarItemType.ADD_STORAGE) {
                        viewModel.onAddStorageClick()
                    } else {
                        val storageType = when (item.type) {
                            SidebarItemType.REMOTE_SERVER -> StorageType.SMB
                            else -> StorageType.LOCAL
                        }
                        item.path?.let { viewModel.onIntent(FileManagerIntent.NavigateTo(it, storageType, item.serverId)) }
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
                    onNavigateTo = { viewModel.onIntent(FileManagerIntent.NavigateTo(it, state.currentStorageType, state.currentServerId)) },
                    onToggleViewMode = { viewModel.onIntent(FileManagerIntent.ToggleViewMode(it)) },
                    onSearchQueryChange = { viewModel.onIntent(FileManagerIntent.Search(it)) }
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
                            onFileClick = { viewModel.onIntent(FileManagerIntent.OpenFile(it)) },
                            onFileLongClick = { viewModel.onIntent(FileManagerIntent.SelectFile(it.path)) }
                        )
                    } else {
                        FileList(
                            files = filteredFiles,
                            selectedFiles = state.selectedFiles,
                            onFileClick = { viewModel.onIntent(FileManagerIntent.OpenFile(it)) },
                            onFileLongClick = { viewModel.onIntent(FileManagerIntent.SelectFile(it.path)) }
                        )
                    }
                }
            }
        }
    }
}
