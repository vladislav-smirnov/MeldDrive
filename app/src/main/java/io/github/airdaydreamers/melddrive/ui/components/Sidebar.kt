package io.github.airdaydreamers.melddrive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.airdaydreamers.melddrive.R
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType

@Composable
fun FileManagerSidebar(items: List<SidebarItem>, currentPath: String, onItemClick: (SidebarItem) -> Unit, modifier: Modifier = Modifier) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                val isSelected = item.path == currentPath
                val title = item.title
                // Convert default system folder names to their translated string resources if matched, or keep original
                val labelText = when (title) {
                    "Home" -> stringResource(R.string.sidebar_home)
                    "Downloads" -> stringResource(R.string.sidebar_downloads)
                    "Photos" -> stringResource(R.string.sidebar_photos)
                    "Movies" -> stringResource(R.string.sidebar_movies)
                    "Music" -> stringResource(R.string.sidebar_music)
                    "Add Storage" -> stringResource(R.string.sidebar_add_storage)
                    else -> title
                }
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onItemClick(item) },
                    icon = {
                        Icon(item.icon, contentDescription = labelText)
                    },
                    label = {
                        Text(labelText, style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.testTag("sidebar_item_${item.title}"),
                )
            }
        }
    }
}

@Composable
fun FileManagerDrawerContent(items: List<SidebarItem>, currentPath: String, onItemClick: (SidebarItem) -> Unit, onDeleteServer: (Long) -> Unit = {}) {
    ModalDrawerSheet {
        DrawerContentBody(items, currentPath, onItemClick, onDeleteServer)
    }
}

/**
 * Permanent drawer variant for expanded screens (tablets, desktop).
 * Uses [PermanentDrawerSheet] which is always visible and not dismissible.
 */
@Composable
fun PermanentDrawerContent(items: List<SidebarItem>, currentPath: String, onItemClick: (SidebarItem) -> Unit, onDeleteServer: (Long) -> Unit = {}) {
    PermanentDrawerSheet {
        DrawerContentBody(items, currentPath, onItemClick, onDeleteServer)
    }
}

/**
 * Shared drawer body content used by both [FileManagerDrawerContent] and [PermanentDrawerContent].
 */
@Composable
private fun DrawerContentBody(items: List<SidebarItem>, currentPath: String, onItemClick: (SidebarItem) -> Unit, onDeleteServer: (Long) -> Unit) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.sidebar_title),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
        )

        val localItems = items.filter { it.type == SidebarItemType.SYSTEM_FOLDER }
        val remoteItems = items.filter { it.type == SidebarItemType.REMOTE_SERVER }
        val actionItems = items.filter { it.type == SidebarItemType.ADD_STORAGE }

        localItems.forEach { item ->
            SidebarDrawerItem(item, currentPath, onItemClick)
        }

        if (remoteItems.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.sidebar_remote_section),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            remoteItems.forEach { item ->
                SidebarDrawerItem(
                    item = item,
                    currentPath = currentPath,
                    onItemClick = onItemClick,
                    onLongClick = { item.serverId?.let { onDeleteServer(it) } },
                )
            }
        }

        if (actionItems.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            actionItems.forEach { item ->
                SidebarDrawerItem(item, currentPath, onItemClick)
            }
        }
    }
}

@Composable
fun SidebarDrawerItem(item: SidebarItem, currentPath: String, onItemClick: (SidebarItem) -> Unit, onLongClick: (() -> Unit)? = null) {
    val isSelected = item.path == currentPath && item.type != SidebarItemType.ADD_STORAGE

    val title = item.title
    val displayLabelText = when (title) {
        "Home" -> stringResource(R.string.sidebar_home)
        "Downloads" -> stringResource(R.string.sidebar_downloads)
        "Photos" -> stringResource(R.string.sidebar_photos)
        "Movies" -> stringResource(R.string.sidebar_movies)
        "Music" -> stringResource(R.string.sidebar_music)
        "Add Storage" -> stringResource(R.string.sidebar_add_storage)
        else -> title
    }

    NavigationDrawerItem(
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(displayLabelText)
                if (item.type == SidebarItemType.REMOTE_SERVER && onLongClick != null) {
                    IconButton(
                        onClick = { onLongClick() },
                        modifier = Modifier.size(24.dp).testTag("delete_server_${item.title}"),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_server),
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        selected = isSelected,
        onClick = { onItemClick(item) },
        icon = { Icon(item.icon, contentDescription = null) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("sidebar_item_${item.title}"),
    )
}
