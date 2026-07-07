package io.github.airdaydreamers.melddrive.data.model

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

data class FileItem(
    val path: Path,
    val name: String = path.name,
    val isDirectory: Boolean = path.isDirectory(),
    val size: Long = if (isDirectory) 0 else try { Files.size(path) } catch (e: Exception) { 0 },
    val lastModified: Long = try { Files.getLastModifiedTime(path).toMillis() } catch (e: Exception) { 0 }
)

enum class SidebarItemType {
    SYSTEM_FOLDER, FAVORITE, TAG
}

data class SidebarItem(
    val id: String,
    val title: String,
    val path: Path?,
    val type: SidebarItemType,
    val icon: Any // TODO: use correct type
)
