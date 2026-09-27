package io.github.airdaydreamers.melddrive.ui.preview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.airdaydreamers.melddrive.data.discovery.DiscoveredServer
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItem
import io.github.airdaydreamers.melddrive.data.model.SidebarItemType
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageState
import io.github.airdaydreamers.melddrive.ui.mvi.FileManagerState
import io.github.airdaydreamers.melddrive.ui.mvi.ServerType
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsState
import io.github.airdaydreamers.melddrive.ui.mvi.ViewMode

object PreviewMockData {
    val sampleFiles = listOf(
        FileItem(path = "/storage/emulated/0/Documents", name = "Documents", isDirectory = true),
        FileItem(path = "/storage/emulated/0/Pictures", name = "Pictures", isDirectory = true),
        FileItem(path = "/storage/emulated/0/photo.jpg", name = "photo.jpg", isDirectory = false, size = 2_097_152L),
        FileItem(path = "/storage/emulated/0/presentation.pdf", name = "presentation.pdf", isDirectory = false, size = 5_242_880L),
        FileItem(path = "/storage/emulated/0/vacation.mp4", name = "vacation.mp4", isDirectory = false, size = 45_000_000L),
    )

    val sampleSidebarItems = listOf(
        SidebarItem("home", "Home", "/storage/emulated/0", SidebarItemType.SYSTEM_FOLDER, Icons.Default.Home),
        SidebarItem("smb_1", "NAS Server", "192.168.1.100", SidebarItemType.REMOTE_SERVER, Icons.Default.Storage, serverId = 1L),
        SidebarItem("add_storage", "Add Storage", null, SidebarItemType.ADD_STORAGE, Icons.Default.Add),
    )

    val sampleDiscoveredServers = listOf(
        DiscoveredServer("Synology NAS", "192.168.1.100", 445, StorageType.SMB),
        DiscoveredServer("Nextcloud Server", "192.168.1.101", 8080, StorageType.WEBDAV),
    )
}

class FileManagerPreviewParameterProvider : PreviewParameterProvider<FileManagerState> {
    override val values: Sequence<FileManagerState> = sequenceOf(
        // 1. Populated List View State
        FileManagerState(
            currentPath = "/storage/emulated/0",
            files = PreviewMockData.sampleFiles,
            sidebarItems = PreviewMockData.sampleSidebarItems,
            viewMode = ViewMode.LIST,
        ),
        // 2. Populated Grid View State
        FileManagerState(
            currentPath = "/storage/emulated/0",
            files = PreviewMockData.sampleFiles,
            sidebarItems = PreviewMockData.sampleSidebarItems,
            viewMode = ViewMode.GRID,
        ),
        // 3. Populated Card View State
        FileManagerState(
            currentPath = "/storage/emulated/0",
            files = PreviewMockData.sampleFiles,
            sidebarItems = PreviewMockData.sampleSidebarItems,
            viewMode = ViewMode.CARD,
        ),
        // 4. Empty Folder State
        FileManagerState(
            currentPath = "/storage/emulated/0/EmptyFolder",
            files = emptyList(),
            sidebarItems = PreviewMockData.sampleSidebarItems,
        ),
        // 5. Loading State
        FileManagerState(
            currentPath = "/storage/emulated/0",
            isLoading = true,
            sidebarItems = PreviewMockData.sampleSidebarItems,
        ),
        // 6. Search Active State
        FileManagerState(
            currentPath = "/storage/emulated/0",
            files = PreviewMockData.sampleFiles,
            sidebarItems = PreviewMockData.sampleSidebarItems,
            searchQuery = "photo",
            isSearchActive = true,
        ),
        // 7. Selected Items Overlay State
        FileManagerState(
            currentPath = "/storage/emulated/0",
            files = PreviewMockData.sampleFiles,
            sidebarItems = PreviewMockData.sampleSidebarItems,
            selectedFiles = setOf("/storage/emulated/0/photo.jpg", "/storage/emulated/0/presentation.pdf"),
        ),
        // 8. Error State
        FileManagerState(
            currentPath = "/storage/emulated/0",
            sidebarItems = PreviewMockData.sampleSidebarItems,
            errorMessage = "Failed to access storage directory",
        ),
    )
}

class SettingsPreviewParameterProvider : PreviewParameterProvider<SettingsState> {
    override val values: Sequence<SettingsState> = sequenceOf(
        // 1. Default Settings State
        SettingsState(),
        // 2. Buffering Enabled with Custom Size
        SettingsState(
            bufferingEnabled = true,
            bufferSizeMb = 32,
            showHiddenFiles = true,
            currentLanguageCode = "en",
        ),
        // 3. Alternate Language & Options Enabled
        SettingsState(
            bufferingEnabled = false,
            bufferSizeMb = 16,
            showHiddenFiles = true,
            currentLanguageCode = "de",
        ),
    )
}

class AddStoragePreviewParameterProvider : PreviewParameterProvider<AddStorageState> {
    override val values: Sequence<AddStorageState> = sequenceOf(
        // 1. Default SMB State
        AddStorageState(
            serverType = ServerType.SMB,
            displayName = "My Home NAS",
            host = "192.168.1.100",
            port = "445",
            username = "admin",
            password = "password123",
        ),
        // 2. WebDAV State with Trust Self Signed
        AddStorageState(
            serverType = ServerType.WEBDAV,
            displayName = "Cloud WebDAV",
            host = "webdav.example.com",
            port = "443",
            username = "user",
            password = "secretpassword",
            trustSelfSigned = true,
        ),
        // 3. Loading / Connecting State
        AddStorageState(
            serverType = ServerType.SMB,
            displayName = "Home Storage",
            host = "192.168.1.100",
            isLoading = true,
        ),
        // 4. Discovered Servers Active State
        AddStorageState(
            serverType = ServerType.SMB,
            isDiscovering = false,
            discoveredServers = PreviewMockData.sampleDiscoveredServers,
        ),
        // 5. Error State
        AddStorageState(
            serverType = ServerType.SMB,
            displayName = "NAS",
            host = "192.168.1.200",
            error = "Could not connect to host 192.168.1.200: Connection timed out",
        ),
    )
}
