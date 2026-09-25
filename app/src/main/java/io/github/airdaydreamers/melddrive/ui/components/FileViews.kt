package io.github.airdaydreamers.melddrive.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.VideoThumbnailModel
import io.github.airdaydreamers.melddrive.data.storage.FileStreamProvider
import io.github.airdaydreamers.melddrive.util.MimeTypeMapCompat

private const val ICON_CORNER_RADIUS_DP = 6
private const val FALLBACK_ICON_SIZE_RATIO = 0.6f
private const val BADGE_CONTAINER_SIZE_RATIO = 0.45f
private const val BADGE_ICON_SIZE_RATIO = 0.35f
private const val BADGE_BACKGROUND_ALPHA = 0.5f

@Composable
fun FileList(files: List<FileItem>, selectedFiles: Set<String>, onFileClick: (FileItem) -> Unit, onFileLongClick: (FileItem) -> Unit, serverId: Long? = null) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(files, key = { it.path }) { file ->
            FileListItem(
                file = file,
                isSelected = selectedFiles.contains(file.path),
                onClick = { onFileClick(file) },
                onLongClick = { onFileLongClick(file) },
                serverId = serverId,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(file: FileItem, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, serverId: Long? = null) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("file_item_${file.name}"),
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIcon(file = file, serverId = serverId, iconSize = 40.dp)
            Spacer(Modifier.width(16.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun FileGrid(files: List<FileItem>, selectedFiles: Set<String>, onFileClick: (FileItem) -> Unit, onFileLongClick: (FileItem) -> Unit, serverId: Long? = null) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(files, key = { it.path }) { file ->
            FileGridItem(
                file = file,
                isSelected = selectedFiles.contains(file.path),
                onClick = { onFileClick(file) },
                onLongClick = { onFileLongClick(file) },
                serverId = serverId,
            )
        }
    }
}

@Composable
fun FileCardGrid(
    files: List<FileItem>,
    selectedFiles: Set<String>,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    serverId: Long? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(files, key = { it.path }) { file ->
            FileCardItem(
                file = file,
                isSelected = selectedFiles.contains(file.path),
                onClick = { onFileClick(file) },
                onLongClick = { onFileLongClick(file) },
                serverId = serverId,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod", "CognitiveComplexMethod")
fun FileCardItem(file: FileItem, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, serverId: Long? = null) {
    val context = LocalContext.current
    val formattedSize = remember(file.size, file.isDirectory) {
        if (file.isDirectory) {
            ""
        } else {
            Formatter.formatShortFileSize(context, file.size)
        }
    }

    val isVideo = !file.isDirectory && MimeTypeMapCompat.isVideoFile(file.name)
    val isImage = !file.isDirectory && MimeTypeMapCompat.isImageFile(file.name)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(6.dp)
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("file_item_${file.name}"),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isVideo) {
                VideoThumbnail(
                    file = file,
                    serverId = serverId,
                    iconSize = 48.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (isImage) {
                val imageUri = remember(file, serverId) {
                    FileStreamProvider.buildUri(file.storageType, serverId, file.path)
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUri)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (file.isDirectory) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // Top-right file size
            if (formattedSize.isNotEmpty()) {
                Text(
                    text = formattedSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Bottom item name with scrim gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            ),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(file: FileItem, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, serverId: Long? = null) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .padding(4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("file_item_${file.name}"),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FileIcon(file = file, serverId = serverId, iconSize = 48.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun FileIcon(file: FileItem, serverId: Long?, iconSize: Dp) {
    val isVideo = !file.isDirectory && MimeTypeMapCompat.isVideoFile(file.name)

    if (file.isDirectory) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary,
        )
    } else if (isVideo) {
        VideoThumbnail(
            file = file,
            serverId = serverId,
            iconSize = iconSize,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
fun VideoThumbnail(file: FileItem, serverId: Long?, iconSize: Dp, modifier: Modifier = Modifier.size(iconSize)) {
    val context = LocalContext.current
    val model = remember(file, serverId) {
        VideoThumbnailModel(file = file, serverId = serverId)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ICON_CORNER_RADIUS_DP.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        var isSuccess by remember(model) { mutableStateOf(false) }

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            onSuccess = { isSuccess = true },
            onError = { isSuccess = false },
            modifier = Modifier.fillMaxSize(),
        )

        if (!isSuccess) {
            Icon(
                imageVector = Icons.Default.VideoFile,
                contentDescription = null,
                modifier = Modifier.size(iconSize * FALLBACK_ICON_SIZE_RATIO),
                tint = MaterialTheme.colorScheme.outline,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(iconSize * BADGE_CONTAINER_SIZE_RATIO)
                    .background(Color.Black.copy(alpha = BADGE_BACKGROUND_ALPHA), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize * BADGE_ICON_SIZE_RATIO),
                    tint = Color.White,
                )
            }
        }
    }
}
