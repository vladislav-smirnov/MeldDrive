package io.github.airdaydreamers.melddrive.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerTopBar(
    currentPath: String,
    isGridView: Boolean,
    searchQuery: String,
    onMenuClick: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onToggleViewMode: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        title = {
            Breadcrumbs(currentPath = currentPath, onNavigateTo = onNavigateTo)
        },
        actions = {
            IconButton(onClick = { /* TODO: Implement search UI expansion */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = { onToggleViewMode(!isGridView) }) {
                Icon(
                    if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle View"
                )
            }
        }
    )
}

@Composable
fun Breadcrumbs(
    currentPath: String,
    onNavigateTo: (String) -> Unit
) {
    val parts = currentPath.split("/").filter { it.isNotEmpty() }

    val breadcrumbItems = mutableListOf<Pair<String, String>>()
    breadcrumbItems.add("Root" to (if (currentPath.startsWith("/")) "/" else ""))

    var runningPath = if (currentPath.startsWith("/")) "/" else ""
    parts.forEachIndexed { index, part ->
        runningPath = if (runningPath == "/" || runningPath == "") runningPath + part else "$runningPath/$part"
        breadcrumbItems.add(part to runningPath)
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
