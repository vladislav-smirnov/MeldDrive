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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
    onMenuClick: (() -> Unit)?,
    onNavigateTo: (String) -> Unit,
    onToggleViewMode: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit = {},
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
            label = "SearchTransition",
        ) { active ->
            if (active) {
                SearchTopBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onCloseSearch = { onSearchActiveChange(false) },
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
                    onSettingsClick = onSettingsClick,
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
    onMenuClick: (() -> Unit)?,
    onNavigateTo: (String) -> Unit,
    onToggleViewMode: (Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }
        },
        title = {
            Breadcrumbs(
                currentPath = currentPath,
                storageType = storageType,
                serverName = serverName,
                onNavigateTo = onNavigateTo,
            )
        },
        actions = {
            IconButton(onClick = onSearchClick, modifier = Modifier.testTag("search_button")) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = { onToggleViewMode(!isGridView) }) {
                Icon(
                    if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle View",
                )
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("settings_button")) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(searchQuery: String, onSearchQueryChange: (String) -> Unit, onCloseSearch: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onCloseSearch, modifier = Modifier.testTag("search_close_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("search_input"),
                placeholder = { Text("Search files...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
        actions = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        },
    )
}

@Composable
fun Breadcrumbs(currentPath: String, storageType: StorageType, serverName: String?, onNavigateTo: (String) -> Unit) {
    val breadcrumbItems = remember(currentPath, storageType, serverName) {
        getBreadcrumbItems(currentPath, storageType, serverName)
    }

    LazyRow(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(breadcrumbItems) { index, (name, path) ->
            TextButton(
                onClick = { onNavigateTo(path) },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (index < breadcrumbItems.size - 1) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun getBreadcrumbItems(currentPath: String, storageType: StorageType, serverName: String?): List<Pair<String, String>> = when (storageType) {
    StorageType.LOCAL -> getLocalBreadcrumbs(currentPath)
    StorageType.SMB -> getSmbBreadcrumbs(currentPath, serverName)
    else -> emptyList()
}

private fun getLocalBreadcrumbs(currentPath: String): List<Pair<String, String>> {
    val items = mutableListOf<Pair<String, String>>()
    val rootPath = System.getProperty("test.local.root") ?: try {
        Environment.getExternalStorageDirectory().absolutePath
    } catch (_: Exception) {
        // Fallback for preview mode or when system services are unavailable
        "/storage/emulated/0"
    }
    items.add("Internal Storage" to rootPath)

    if (currentPath.startsWith(rootPath)) {
        val relativePath = currentPath.removePrefix(rootPath).trimStart('/')
        if (relativePath.isNotEmpty()) {
            val parts = relativePath.split('/')
            addLocalBreadcrumbParts(items, parts, rootPath)
        }
    } else {
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        items.add("Root" to "/")
        addRootBreadcrumbParts(items, parts)
    }
    return items
}

private fun addLocalBreadcrumbParts(items: MutableList<Pair<String, String>>, parts: List<String>, rootPath: String) {
    var runningPath = rootPath
    parts.forEach { part ->
        runningPath = if (runningPath.endsWith('/')) runningPath + part else "$runningPath/$part"
        items.add(part to runningPath)
    }
}

private fun addRootBreadcrumbParts(items: MutableList<Pair<String, String>>, parts: List<String>) {
    var runningPath = if (parts.isNotEmpty() && parts[0].isEmpty()) "/" else ""
    parts.forEach { part ->
        runningPath = if (runningPath == "/") runningPath + part else "$runningPath/$part"
        items.add(part to runningPath)
    }
}

private fun getSmbBreadcrumbs(currentPath: String, serverName: String?): List<Pair<String, String>> {
    val items = mutableListOf<Pair<String, String>>()
    items.add((serverName ?: "Remote") to "")
    if (currentPath.isNotEmpty()) {
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        addSmbBreadcrumbParts(items, parts)
    }
    return items
}

private fun addSmbBreadcrumbParts(items: MutableList<Pair<String, String>>, parts: List<String>) {
    var runningPath = ""
    parts.forEach { part ->
        runningPath = if (runningPath.isEmpty()) part else "$runningPath/$part"
        items.add(part to runningPath)
    }
}
