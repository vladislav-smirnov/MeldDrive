package io.github.airdaydreamers.melddrive.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import java.nio.file.Path

@Composable
fun FileManagerSidebar(
    items: List<SidebarItem>,
    currentPath: Path,
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
                        Icon(item.icon as ImageVector, contentDescription = item.title)
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
    currentPath: Path,
    onItemClick: (SidebarItem) -> Unit
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(12.dp))
        Text(
            "File Manager",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge
        )
        items.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.title) },
                selected = item.path == currentPath,
                onClick = { onItemClick(item) },
                icon = { Icon(item.icon as ImageVector, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}
