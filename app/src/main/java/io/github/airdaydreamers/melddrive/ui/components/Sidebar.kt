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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onItemClick(item) },
                    icon = {
                        Icon(item.icon, contentDescription = item.title)
                    },
                    label = {
                        Text(item.title, style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }
    }
}

@Composable
fun FileManagerDrawerContent(items: List<SidebarItem>, currentPath: String, onItemClick: (SidebarItem) -> Unit, onDeleteServer: (Long) -> Unit = {}) {
    ModalDrawerSheet {
        Spacer(Modifier.height(12.dp))
        Text(
            "File Manager",
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
                "Remote",
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

    NavigationDrawerItem(
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(item.title)
                if (item.type == SidebarItemType.REMOTE_SERVER && onLongClick != null) {
                    IconButton(
                        onClick = { onLongClick() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Server",
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
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
