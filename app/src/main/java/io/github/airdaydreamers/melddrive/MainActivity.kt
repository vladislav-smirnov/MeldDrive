package io.github.airdaydreamers.melddrive

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.storage.FileStreamProvider
import io.github.airdaydreamers.melddrive.ui.components.AdaptiveNavigation.calculateNavigationType
import io.github.airdaydreamers.melddrive.ui.navigation.AddStorage
import io.github.airdaydreamers.melddrive.ui.navigation.FileManager
import io.github.airdaydreamers.melddrive.ui.navigation.MeldDriveKey
import io.github.airdaydreamers.melddrive.ui.screens.AddStorageScreen
import io.github.airdaydreamers.melddrive.ui.screens.FileManagerScreen
import io.github.airdaydreamers.melddrive.ui.screens.SettingsScreen
import io.github.airdaydreamers.melddrive.ui.theme.MeldDriveTheme
import java.io.File
import android.provider.Settings as AndroidSettings
import io.github.airdaydreamers.melddrive.ui.navigation.Settings as MeldDriveSettings

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        setContent {
            MeldDriveTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                val navigationType = calculateNavigationType(windowSizeClass.widthSizeClass)

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val backStack = rememberMeldDriveNavBackStack(FileManager)

                    val entryProvider = entryProvider<MeldDriveKey> {
                        entry<FileManager> {
                            FileManagerScreen(
                                navigationType = navigationType,
                                onOpenFile = { effect ->
                                    openFile(effect.fileItem, effect.serverId)
                                },
                                onShowToast = { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() },
                                onNavigateToAddStorage = { backStack.add(AddStorage) },
                                onNavigateToSettings = { backStack.add(MeldDriveSettings) },
                            )
                        }
                        entry<AddStorage> {
                            AddStorageScreen(
                                onBack = { backStack.removeLastOrNull() },
                                onSuccess = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<MeldDriveSettings> {
                            SettingsScreen(onBack = { backStack.removeLastOrNull() })
                        }
                    }

                    NavDisplay(
                        backStack = backStack,
                        entryProvider = entryProvider,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator<MeldDriveKey>(),
                            rememberViewModelStoreNavEntryDecorator<MeldDriveKey>(),
                        ),
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:${applicationContext.packageName}".toUri()
            startActivity(intent)
        }
    }

    private fun openFile(fileItem: FileItem, serverId: Long? = null) {
        val uri = if (fileItem.storageType == StorageType.LOCAL) {
            val file = File(fileItem.path)
            FileProvider.getUriForFile(
                this,
                "$applicationId.provider",
                file,
            )
        } else {
            FileStreamProvider.buildUri(fileItem.storageType, serverId, fileItem.path)
        }

        val mimeType = getMimeType(fileItem.name)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app found to open this file type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast(".", "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    private val applicationId: String
        get() = packageName
}

@Composable
fun rememberMeldDriveNavBackStack(vararg elements: MeldDriveKey): NavBackStack<MeldDriveKey> =
    rememberSerializable(serializer = NavBackStackSerializer<MeldDriveKey>()) {
        NavBackStack(*elements)
    }
