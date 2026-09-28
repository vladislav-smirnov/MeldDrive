package io.github.airdaydreamers.melddrive.ui.preview

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.ui.components.AdaptiveNavigation.NavigationType
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageIntent
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageState
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerIntent
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerState
import io.github.airdaydreamers.melddrive.ui.mvi.ServerType
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsIntent
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsState
import io.github.airdaydreamers.melddrive.ui.screens.AddStorageContent
import io.github.airdaydreamers.melddrive.ui.screens.FileManagerContent
import io.github.airdaydreamers.melddrive.ui.screens.SettingsContent
import io.github.airdaydreamers.melddrive.ui.theme.MeldDriveTheme

/**
 * Interactive preview wrapper for [FileManagerContent] that maintains state internally
 * to support Android Studio Interactive Preview Mode.
 *
 * Dynamically calculates [NavigationType] based on preview constraints if not explicitly provided:
 * - width < 600dp: Compact / DRAWER
 * - 600dp <= width < 840dp: Medium / RAIL
 * - width >= 840dp: Expanded / PERMANENT_DRAWER
 */
@Composable
fun FileManagerPreviewWrapper(
    initialState: FileManagerState = FileManagerPreviewParameterProvider().values.first(),
    navigationType: NavigationType? = null,
    onNavigateToAddStorage: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    state: FileManagerState? = null,
    onStateChange: ((FileManagerState) -> Unit)? = null,
) {
    var localState by remember { mutableStateOf(initialState) }
    val currentState = state ?: localState
    val updateState: (FileManagerState) -> Unit = { newState ->
        if ((state != null) && (onStateChange != null)) {
            onStateChange(newState)
        } else {
            localState = newState
        }
    }

    BoxWithConstraints {
        val effectiveNavigationType = navigationType ?: when {
            maxWidth < 600.dp -> NavigationType.DRAWER
            maxWidth < 840.dp -> NavigationType.RAIL
            else -> NavigationType.PERMANENT_DRAWER
        }

        MeldDriveTheme {
            FileManagerContent(
                state = currentState,
                navigationType = effectiveNavigationType,
                onIntent = { intent ->
                    when (intent) {
                        is FileManagerIntent.ToggleViewMode -> {
                            updateState(currentState.copy(viewMode = intent.viewMode))
                        }

                        is FileManagerIntent.Search -> {
                            updateState(currentState.copy(searchQuery = intent.query))
                        }

                        is FileManagerIntent.SetSearchActive -> {
                            updateState(
                                currentState.copy(
                                    isSearchActive = intent.isActive,
                                    searchQuery = if (!intent.isActive) "" else currentState.searchQuery,
                                ),
                            )
                        }

                        is FileManagerIntent.SelectFile -> {
                            val newSelection = currentState.selectedFiles.toMutableSet()
                            if (newSelection.contains(intent.path)) {
                                newSelection.remove(intent.path)
                            } else {
                                newSelection.add(intent.path)
                            }
                            updateState(currentState.copy(selectedFiles = newSelection))
                        }

                        is FileManagerIntent.NavigateTo -> {
                            val newFiles = if (intent.path == "/storage/emulated/0") {
                                PreviewMockData.sampleFiles
                            } else {
                                emptyList()
                            }
                            updateState(
                                currentState.copy(
                                    currentPath = intent.path,
                                    currentStorageType = intent.storageType,
                                    currentServerId = intent.serverId,
                                    files = newFiles,
                                    selectedFiles = emptySet(),
                                ),
                            )
                        }

                        FileManagerIntent.NavigateUp -> {
                            val parentPath = currentState.currentPath.substringBeforeLast('/', "")
                            val newPath = parentPath.ifEmpty { "/" }
                            val newFiles = if (newPath == "/storage/emulated/0") {
                                PreviewMockData.sampleFiles
                            } else {
                                emptyList()
                            }
                            updateState(
                                currentState.copy(
                                    currentPath = newPath,
                                    files = newFiles,
                                    selectedFiles = emptySet(),
                                ),
                            )
                        }

                        is FileManagerIntent.OpenFile -> {
                            if (intent.fileItem.isDirectory) {
                                val newFiles = if (intent.fileItem.path == "/storage/emulated/0") {
                                    PreviewMockData.sampleFiles
                                } else {
                                    emptyList()
                                }
                                updateState(
                                    currentState.copy(
                                        currentPath = intent.fileItem.path,
                                        files = newFiles,
                                        selectedFiles = emptySet(),
                                    ),
                                )
                            }
                        }

                        is FileManagerIntent.DeleteFiles -> {
                            val updatedFiles = currentState.files.filterNot { intent.paths.contains(it.path) }
                            updateState(
                                currentState.copy(
                                    files = updatedFiles,
                                    selectedFiles = emptySet(),
                                ),
                            )
                        }

                        is FileManagerIntent.RenameFile -> {
                            val updatedFiles = currentState.files.map { item ->
                                if (item.path == intent.path) {
                                    val newPath = item.path.substringBeforeLast('/') + "/" + intent.newName
                                    item.copy(name = intent.newName, path = newPath)
                                } else {
                                    item
                                }
                            }
                            updateState(currentState.copy(files = updatedFiles, selectedFiles = emptySet()))
                        }

                        is FileManagerIntent.CreateFolder -> {
                            val newFolder = FileItem(
                                path = "${currentState.currentPath}/${intent.name}",
                                name = intent.name,
                                isDirectory = true,
                            )
                            updateState(currentState.copy(files = currentState.files + newFolder))
                        }

                        FileManagerIntent.NavigateToAddStorage -> {
                            onNavigateToAddStorage()
                        }

                        FileManagerIntent.NavigateToSettings -> {
                            onNavigateToSettings()
                        }

                        is FileManagerIntent.DeleteRemoteServer -> {
                            val updatedSidebar = currentState.sidebarItems.filterNot { it.serverId == intent.serverId }
                            updateState(currentState.copy(sidebarItems = updatedSidebar))
                        }

                        FileManagerIntent.Refresh -> {
                            // Refresh simulated reset / loading
                        }
                    }
                },
                onDeleteServer = { serverId ->
                    val updatedSidebar = currentState.sidebarItems.filterNot { it.serverId == serverId }
                    updateState(currentState.copy(sidebarItems = updatedSidebar))
                },
            )
        }
    }
}

/**
 * Interactive preview wrapper for [SettingsContent] that maintains state internally
 * to support Android Studio Interactive Preview Mode.
 */
@Composable
fun SettingsPreviewWrapper(
    initialState: SettingsState = SettingsPreviewParameterProvider().values.first(),
    onBack: () -> Unit = {},
    state: SettingsState? = null,
    onStateChange: ((SettingsState) -> Unit)? = null,
) {
    var localState by remember { mutableStateOf(initialState) }
    val currentState = state ?: localState
    val updateState: (SettingsState) -> Unit = { newState ->
        if ((state != null) && (onStateChange != null)) {
            onStateChange(newState)
        } else {
            localState = newState
        }
    }

    MeldDriveTheme {
        SettingsContent(
            state = currentState,
            onIntent = { intent ->
                when (intent) {
                    is SettingsIntent.SetBufferingEnabled -> {
                        updateState(currentState.copy(bufferingEnabled = intent.enabled))
                    }

                    is SettingsIntent.SetBufferSizeMb -> {
                        updateState(currentState.copy(bufferSizeMb = intent.sizeMb))
                    }

                    is SettingsIntent.SetShowHiddenFiles -> {
                        updateState(currentState.copy(showHiddenFiles = intent.show))
                    }

                    is SettingsIntent.SetLanguage -> {
                        updateState(currentState.copy(currentLanguageCode = intent.languageCode))
                    }
                }
            },
            onBack = onBack,
        )
    }
}

/**
 * Interactive preview wrapper for [AddStorageContent] that maintains state internally
 * to support Android Studio Interactive Preview Mode.
 */
@Composable
fun AddStoragePreviewWrapper(
    initialState: AddStorageState = AddStoragePreviewParameterProvider().values.first(),
    onBack: () -> Unit = {},
    onSuccess: () -> Unit = {},
    state: AddStorageState? = null,
    onStateChange: ((AddStorageState) -> Unit)? = null,
) {
    var localState by remember { mutableStateOf(initialState) }
    val currentState = state ?: localState
    val updateState: (AddStorageState) -> Unit = { newState ->
        if ((state != null) && (onStateChange != null)) {
            onStateChange(newState)
        } else {
            localState = newState
        }
    }

    MeldDriveTheme {
        AddStorageContent(
            state = currentState,
            onIntent = { intent ->
                when (intent) {
                    is AddStorageIntent.ServerTypeChange -> {
                        val defaultPort = if (intent.value == ServerType.SMB) "445" else "443"
                        updateState(currentState.copy(serverType = intent.value, port = defaultPort))
                    }

                    is AddStorageIntent.DisplayNameChange -> {
                        updateState(currentState.copy(displayName = intent.value))
                    }

                    is AddStorageIntent.HostChange -> {
                        updateState(currentState.copy(host = intent.value))
                    }

                    is AddStorageIntent.PortChange -> {
                        updateState(currentState.copy(port = intent.value))
                    }

                    is AddStorageIntent.UsernameChange -> {
                        updateState(currentState.copy(username = intent.value))
                    }

                    is AddStorageIntent.PasswordChange -> {
                        updateState(currentState.copy(password = intent.value))
                    }

                    is AddStorageIntent.AnonymousChange -> {
                        updateState(currentState.copy(isAnonymous = intent.value))
                    }

                    is AddStorageIntent.TrustSelfSignedChange -> {
                        updateState(currentState.copy(trustSelfSigned = intent.value))
                    }

                    is AddStorageIntent.SelectDiscoveredServer -> {
                        val type = when (intent.server.type) {
                            StorageType.SMB -> ServerType.SMB
                            StorageType.WEBDAV -> ServerType.WEBDAV
                            else -> ServerType.SMB
                        }
                        updateState(
                            currentState.copy(
                                host = intent.server.host,
                                port = intent.server.port.toString(),
                                serverType = type,
                                displayName = intent.server.name,
                            ),
                        )
                    }

                    AddStorageIntent.StartDiscovery -> {
                        updateState(
                            currentState.copy(
                                isDiscovering = !currentState.isDiscovering,
                                discoveredServers = currentState.discoveredServers.ifEmpty {
                                    PreviewMockData.sampleDiscoveredServers
                                },
                            ),
                        )
                    }

                    AddStorageIntent.SaveServer -> {
                        onSuccess()
                    }
                }
            },
            onBack = onBack,
        )
    }
}
