package io.github.airdaydreamers.melddrive.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.nio.file.Path

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerTopBar(
    currentPath: Path,
    isGridView: Boolean,
    searchQuery: String,
    onNavigateTo: (Path) -> Unit,
    onToggleViewMode: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    TopAppBar(
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
    currentPath: Path,
    onNavigateTo: (Path) -> Unit
) {
    val parts = mutableListOf<Path>()
    var tempPath: Path? = currentPath
    while (tempPath != null) {
        parts.add(0, tempPath)
        tempPath = tempPath.parent
    }

    LazyRow(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(parts) { index, path ->
            TextButton(
                onClick = { onNavigateTo(path) },
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text(
                    text = if (path.fileName == null) "Device" else path.fileName.toString(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (index < parts.size - 1) {
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
