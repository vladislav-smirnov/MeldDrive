package io.github.airdaydreamers.melddrive.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType

@Composable
fun FileManagerSidebar(
    items: List<SidebarItem>,
    currentPath: String,
    onItemClick: (SidebarItem) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    }
                )
            }
        }
    }
}

@Composable
fun FileManagerDrawerContent(
    items: List<SidebarItem>,
    currentPath: String,
    onItemClick: (SidebarItem) -> Unit
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(12.dp))
        Text(
            "File Manager",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge
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
                color = MaterialTheme.colorScheme.primary
            )
            remoteItems.forEach { item ->
                SidebarDrawerItem(item, currentPath, onItemClick)
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
fun SidebarDrawerItem(
    item: SidebarItem,
    currentPath: String,
    onItemClick: (SidebarItem) -> Unit
) {
    NavigationDrawerItem(
        label = { Text(item.title) },
        selected = item.path == currentPath && item.type != SidebarItemType.ADD_STORAGE,
        onClick = { onItemClick(item) },
        icon = { Icon(item.icon, contentDescription = null) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
