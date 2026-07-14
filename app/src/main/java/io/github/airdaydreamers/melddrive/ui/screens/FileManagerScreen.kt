package io.github.airdaydreamers.melddrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.ui.components.AdaptiveNavigation.NavigationType
import io.github.airdaydreamers.melddrive.ui.components.FileGrid
import io.github.airdaydreamers.melddrive.ui.components.FileList
import io.github.airdaydreamers.melddrive.ui.components.FileManagerDrawerContent
import io.github.airdaydreamers.melddrive.ui.components.FileManagerSidebar
import io.github.airdaydreamers.melddrive.ui.components.FileManagerTopBar
import io.github.airdaydreamers.melddrive.ui.components.PermanentDrawerContent
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerEffect
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerIntent
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerState
import io.github.airdaydreamers.melddrive.ui.theme.MeldDriveTheme
import io.github.airdaydreamers.melddrive.ui.viewmodel.FileManagerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun FileManagerScreen(
    navigationType: NavigationType,
    onOpenFile: (FileManagerEffect.OpenFileExternally) -> Unit,
    onShowToast: (String) -> Unit,
    onNavigateToAddStorage: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: FileManagerViewModel = hiltViewModel(),
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
        navigationType = navigationType,
        onIntent = viewModel::onIntent,
        onDeleteServer = { serverToDelete = it },
    )
}

@Composable
fun FileManagerContent(state: FileManagerState, navigationType: NavigationType, onIntent: (FileManagerIntent) -> Unit, onDeleteServer: (Long) -> Unit = {}) {
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

    when (navigationType) {
        NavigationType.DRAWER -> {
            DrawerLayout(
                state = state,
                currentServerName = currentServerName,
                onIntent = onIntent,
                onDeleteServer = onDeleteServer,
                scope = scope,
            )
        }

        NavigationType.RAIL -> {
            RailLayout(
                state = state,
                currentServerName = currentServerName,
                onIntent = onIntent,
            )
        }

        NavigationType.PERMANENT_DRAWER -> {
            PermanentDrawerLayout(
                state = state,
                currentServerName = currentServerName,
                onIntent = onIntent,
                onDeleteServer = onDeleteServer,
            )
        }
    }
}

/**
 * Compact layout: ModalNavigationDrawer with hamburger menu.
 * Used on phones in portrait mode.
 */
@Composable
private fun DrawerLayout(
    state: FileManagerState,
    currentServerName: String?,
    onIntent: (FileManagerIntent) -> Unit,
    onDeleteServer: (Long) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FileManagerDrawerContent(
                items = state.sidebarItems,
                currentPath = state.currentPath,
                onDeleteServer = onDeleteServer,
                onItemClick = { item ->
                    handleSidebarItemClick(item, onIntent)
                    scope.launch { drawerState.close() }
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

/**
 * Medium layout: NavigationRail alongside content.
 * Used on foldable devices and phones in landscape.
 */
@Composable
private fun RailLayout(state: FileManagerState, currentServerName: String?, onIntent: (FileManagerIntent) -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        FileManagerSidebar(
            items = state.sidebarItems,
            currentPath = state.currentPath,
            onItemClick = { item -> handleSidebarItemClick(item, onIntent) },
        )
        FileManagerMainScaffold(
            state = state,
            currentServerName = currentServerName,
            onIntent = onIntent,
            onMenuClick = null,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Expanded layout: PermanentNavigationDrawer with full drawer always visible.
 * Used on tablets and desktop.
 */
@Composable
private fun PermanentDrawerLayout(state: FileManagerState, currentServerName: String?, onIntent: (FileManagerIntent) -> Unit, onDeleteServer: (Long) -> Unit) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerContent(
                items = state.sidebarItems,
                currentPath = state.currentPath,
                onDeleteServer = onDeleteServer,
                onItemClick = { item -> handleSidebarItemClick(item, onIntent) },
            )
        },
    ) {
        FileManagerMainScaffold(
            state = state,
            currentServerName = currentServerName,
            onIntent = onIntent,
            onMenuClick = null,
        )
    }
}

private fun handleSidebarItemClick(item: SidebarItem, onIntent: (FileManagerIntent) -> Unit) {
    if (item.type == SidebarItemType.ADD_STORAGE) {
        onIntent(FileManagerIntent.NavigateToAddStorage)
    } else {
        val storageType = when (item.type) {
            SidebarItemType.REMOTE_SERVER -> StorageType.SMB
            else -> StorageType.LOCAL
        }
        item.path?.let { onIntent(FileManagerIntent.NavigateTo(it, storageType, item.serverId)) }
    }
}

@Composable
private fun FileManagerMainScaffold(
    state: FileManagerState,
    currentServerName: String?,
    onIntent: (FileManagerIntent) -> Unit,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
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

@Preview(showBackground = true)
@Composable
fun FileManagerScreenPreview() {
    val mockFiles = listOf(
        FileItem(path = "/Documents", name = "Documents", isDirectory = true),
        FileItem(path = "/Downloads", name = "Downloads", isDirectory = true),
        FileItem(path = "/photo.jpg", name = "photo.jpg", isDirectory = false, size = 1024 * 1024),
        FileItem(path = "/report.pdf", name = "report.pdf", isDirectory = false, size = 512 * 1024),
    )

    val mockSidebarItems = listOf(
        SidebarItem("home", "Home", "/", SidebarItemType.SYSTEM_FOLDER, Icons.Default.Home),
        SidebarItem("remote_1", "SMB Server", "", SidebarItemType.REMOTE_SERVER, Icons.Default.Storage, 1),
    )

    val state = FileManagerState(
        currentPath = "/storage/emulated/0",
        files = mockFiles,
        sidebarItems = mockSidebarItems,
    )

    MeldDriveTheme(dynamicColor = false) {
        FileManagerContent(
            state = state,
            navigationType = NavigationType.DRAWER,
            onIntent = {},
        )
    }
}
