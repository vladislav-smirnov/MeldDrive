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
) {
    var state by remember { mutableStateOf(initialState) }

    BoxWithConstraints {
        val effectiveNavigationType = navigationType ?: when {
            maxWidth < 600.dp -> NavigationType.DRAWER
            maxWidth < 840.dp -> NavigationType.RAIL
            else -> NavigationType.PERMANENT_DRAWER
        }

        MeldDriveTheme {
            FileManagerContent(
                state = state,
                navigationType = effectiveNavigationType,
                onIntent = { intent ->
                    when (intent) {
                        is FileManagerIntent.ToggleViewMode -> {
                            state = state.copy(viewMode = intent.viewMode)
                        }

                        is FileManagerIntent.Search -> {
                            state = state.copy(searchQuery = intent.query)
                        }

                        is FileManagerIntent.SetSearchActive -> {
                            state = state.copy(
                                isSearchActive = intent.isActive,
                                searchQuery = if (!intent.isActive) "" else state.searchQuery,
                            )
                        }

                        is FileManagerIntent.SelectFile -> {
                            val newSelection = state.selectedFiles.toMutableSet()
                            if (newSelection.contains(intent.path)) {
                                newSelection.remove(intent.path)
                            } else {
                                newSelection.add(intent.path)
                            }
                            state = state.copy(selectedFiles = newSelection)
                        }

                        is FileManagerIntent.NavigateTo -> {
                            state = state.copy(
                                currentPath = intent.path,
                                currentStorageType = intent.storageType,
                                currentServerId = intent.serverId,
                                selectedFiles = emptySet(),
                            )
                        }

                        FileManagerIntent.NavigateUp -> {
                            val parentPath = state.currentPath.substringBeforeLast('/', "")
                            state = state.copy(
                                currentPath = parentPath.ifEmpty { "/" },
                                selectedFiles = emptySet(),
                            )
                        }

                        is FileManagerIntent.OpenFile -> {
                            if (intent.fileItem.isDirectory) {
                                state = state.copy(
                                    currentPath = intent.fileItem.path,
                                    selectedFiles = emptySet(),
                                )
                            }
                        }

                        is FileManagerIntent.DeleteFiles -> {
                            val updatedFiles = state.files.filterNot { intent.paths.contains(it.path) }
                            state = state.copy(
                                files = updatedFiles,
                                selectedFiles = emptySet(),
                            )
                        }

                        is FileManagerIntent.RenameFile -> {
                            val updatedFiles = state.files.map { item ->
                                if (item.path == intent.path) {
                                    val newPath = item.path.substringBeforeLast('/') + "/" + intent.newName
                                    item.copy(name = intent.newName, path = newPath)
                                } else {
                                    item
                                }
                            }
                            state = state.copy(files = updatedFiles, selectedFiles = emptySet())
                        }

                        is FileManagerIntent.CreateFolder -> {
                            val newFolder = FileItem(
                                path = "${state.currentPath}/${intent.name}",
                                name = intent.name,
                                isDirectory = true,
                            )
                            state = state.copy(files = state.files + newFolder)
                        }

                        FileManagerIntent.NavigateToAddStorage -> {
                            onNavigateToAddStorage()
                        }

                        FileManagerIntent.NavigateToSettings -> {
                            onNavigateToSettings()
                        }

                        is FileManagerIntent.DeleteRemoteServer -> {
                            val updatedSidebar = state.sidebarItems.filterNot { it.serverId == intent.serverId }
                            state = state.copy(sidebarItems = updatedSidebar)
                        }

                        FileManagerIntent.Refresh -> {
                            // Refresh simulated reset / loading
                        }
                    }
                },
                onDeleteServer = { serverId ->
                    val updatedSidebar = state.sidebarItems.filterNot { it.serverId == serverId }
                    state = state.copy(sidebarItems = updatedSidebar)
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
fun SettingsPreviewWrapper(initialState: SettingsState = SettingsPreviewParameterProvider().values.first(), onBack: () -> Unit = {}) {
    var state by remember { mutableStateOf(initialState) }

    MeldDriveTheme {
        SettingsContent(
            state = state,
            onIntent = { intent ->
                when (intent) {
                    is SettingsIntent.SetBufferingEnabled -> {
                        state = state.copy(bufferingEnabled = intent.enabled)
                    }

                    is SettingsIntent.SetBufferSizeMb -> {
                        state = state.copy(bufferSizeMb = intent.sizeMb)
                    }

                    is SettingsIntent.SetShowHiddenFiles -> {
                        state = state.copy(showHiddenFiles = intent.show)
                    }

                    is SettingsIntent.SetLanguage -> {
                        state = state.copy(currentLanguageCode = intent.languageCode)
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
) {
    var state by remember { mutableStateOf(initialState) }

    MeldDriveTheme {
        AddStorageContent(
            state = state,
            onIntent = { intent ->
                when (intent) {
                    is AddStorageIntent.ServerTypeChange -> {
                        val defaultPort = if (intent.value == ServerType.SMB) "445" else "443"
                        state = state.copy(serverType = intent.value, port = defaultPort)
                    }

                    is AddStorageIntent.DisplayNameChange -> {
                        state = state.copy(displayName = intent.value)
                    }

                    is AddStorageIntent.HostChange -> {
                        state = state.copy(host = intent.value)
                    }

                    is AddStorageIntent.PortChange -> {
                        state = state.copy(port = intent.value)
                    }

                    is AddStorageIntent.UsernameChange -> {
                        state = state.copy(username = intent.value)
                    }

                    is AddStorageIntent.PasswordChange -> {
                        state = state.copy(password = intent.value)
                    }

                    is AddStorageIntent.AnonymousChange -> {
                        state = state.copy(isAnonymous = intent.value)
                    }

                    is AddStorageIntent.TrustSelfSignedChange -> {
                        state = state.copy(trustSelfSigned = intent.value)
                    }

                    is AddStorageIntent.SelectDiscoveredServer -> {
                        val type = when (intent.server.type) {
                            StorageType.SMB -> ServerType.SMB
                            StorageType.WEBDAV -> ServerType.WEBDAV
                            else -> ServerType.SMB
                        }
                        state = state.copy(
                            host = intent.server.host,
                            port = intent.server.port.toString(),
                            serverType = type,
                            displayName = intent.server.name,
                        )
                    }

                    AddStorageIntent.StartDiscovery -> {
                        state = state.copy(
                            isDiscovering = !state.isDiscovering,
                            discoveredServers = if (state.discoveredServers.isEmpty()) {
                                PreviewMockData.sampleDiscoveredServers
                            } else {
                                state.discoveredServers
                            },
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
