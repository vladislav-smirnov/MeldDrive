package io.github.airdaydreamers.melddrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerEffect
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerIntent
import io.github.airdaydreamers.melddrive.ui.viewmodel.FileManagerViewModel
import io.github.airdaydreamers.melddrive.ui.components.FileGrid
import io.github.airdaydreamers.melddrive.ui.components.FileList
import io.github.airdaydreamers.melddrive.ui.components.FileManagerDrawerContent
import io.github.airdaydreamers.melddrive.ui.components.FileManagerTopBar
import kotlinx.coroutines.flow.collectLatest

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel = viewModel(),
    onOpenFile: (FileItem) -> Unit,
    onShowToast: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FileManagerEffect.OpenFileExternally -> onOpenFile(effect.fileItem)
                is FileManagerEffect.ShowToast -> onShowToast(effect.message)
            }
        }
    }

    BackHandler(enabled = state.currentPath.parent != null) {
        viewModel.onIntent(FileManagerIntent.NavigateUp)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FileManagerDrawerContent(
                items = state.sidebarItems,
                currentPath = state.currentPath,
                onItemClick = { item ->
                    item.path?.let { viewModel.onIntent(FileManagerIntent.NavigateTo(it)) }
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
                    onNavigateTo = { viewModel.onIntent(FileManagerIntent.NavigateTo(it)) },
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
