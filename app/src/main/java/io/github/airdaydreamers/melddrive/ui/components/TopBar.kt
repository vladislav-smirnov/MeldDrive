package io.github.airdaydreamers.melddrive.ui.components

import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.airdaydreamers.melddrive.data.model.StorageType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerTopBar(
    currentPath: String,
    storageType: StorageType,
    serverName: String?,
    isGridView: Boolean,
    searchQuery: String,
    isSearchActive: Boolean,
    onMenuClick: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onToggleViewMode: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "SearchTransition"
        ) { active ->
            if (active) {
                SearchTopBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onCloseSearch = { onSearchActiveChange(false) }
                )
            } else {
                DefaultTopBar(
                    currentPath = currentPath,
                    storageType = storageType,
                    serverName = serverName,
                    isGridView = isGridView,
                    onMenuClick = onMenuClick,
                    onNavigateTo = onNavigateTo,
                    onToggleViewMode = onToggleViewMode,
                    onSearchClick = { onSearchActiveChange(true) },
                    onSettingsClick = onSettingsClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultTopBar(
    currentPath: String,
    storageType: StorageType,
    serverName: String?,
    isGridView: Boolean,
    onMenuClick: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onToggleViewMode: (Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        title = {
            Breadcrumbs(
                currentPath = currentPath,
                storageType = storageType,
                serverName = serverName,
                onNavigateTo = onNavigateTo
            )
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = { onToggleViewMode(!isGridView) }) {
                Icon(
                    if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle View"
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Search files...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        },
        actions = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        }
    )
}

@Composable
fun Breadcrumbs(
    currentPath: String,
    storageType: StorageType,
    serverName: String?,
    onNavigateTo: (String) -> Unit
) {
    val breadcrumbItems = remember(currentPath, storageType, serverName) {
        val items = mutableListOf<Pair<String, String>>()

        if (storageType == StorageType.LOCAL) {
            val rootPath = Environment.getExternalStorageDirectory().absolutePath
            items.add("Internal Storage" to rootPath)

            if (currentPath.startsWith(rootPath)) {
                val relativePath = currentPath.removePrefix(rootPath).trimStart('/')
                if (relativePath.isNotEmpty()) {
                    val parts = relativePath.split('/')
                    var runningPath = rootPath
                    parts.forEach { part ->
                        runningPath = if (runningPath.endsWith('/')) runningPath + part else "$runningPath/$part"
                        items.add(part to runningPath)
                    }
                }
            } else {
                // Fallback for paths outside external storage (if any)
                val parts = currentPath.split("/").filter { it.isNotEmpty() }
                var runningPath = if (currentPath.startsWith("/")) "/" else ""
                if (parts.isEmpty()) {
                    items.add("Root" to "/")
                } else {
                    items.add("Root" to "/")
                    parts.forEach { part ->
                        runningPath = if (runningPath == "/") runningPath + part else "$runningPath/$part"
                        items.add(part to runningPath)
                    }
                }
            }
        } else if (storageType == StorageType.SMB) {
            items.add((serverName ?: "Remote") to "")
            if (currentPath.isNotEmpty()) {
                val parts = currentPath.split("/").filter { it.isNotEmpty() }
                var runningPath = ""
                parts.forEach { part ->
                    runningPath = if (runningPath.isEmpty()) part else "$runningPath/$part"
                    items.add(part to runningPath)
                }
            }
        }

        items
    }

    LazyRow(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(breadcrumbItems) { index, (name, path) ->
            TextButton(
                onClick = { onNavigateTo(path) },
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (index < breadcrumbItems.size - 1) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
