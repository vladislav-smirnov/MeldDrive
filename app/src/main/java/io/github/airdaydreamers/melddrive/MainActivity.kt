package io.github.airdaydreamers.melddrive

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.airdaydreamers.melddrive.data.db.AppDatabase
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.data.model.StorageType
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.security.CredentialStorage
import io.github.airdaydreamers.melddrive.data.security.SecurityManager
import io.github.airdaydreamers.melddrive.data.storage.FileStreamProvider
import io.github.airdaydreamers.melddrive.ui.screens.AddStorageScreen
import io.github.airdaydreamers.melddrive.ui.screens.FileManagerScreen
import io.github.airdaydreamers.melddrive.ui.screens.SettingsScreen
import io.github.airdaydreamers.melddrive.ui.theme.MeldDriveTheme
import io.github.airdaydreamers.melddrive.ui.viewmodel.AddStorageViewModel
import io.github.airdaydreamers.melddrive.ui.viewmodel.FileManagerViewModel
import io.github.airdaydreamers.melddrive.ui.viewmodel.ViewModelFactory
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        val database = AppDatabase.getDatabase(this)
        val securityManager = SecurityManager(this)
        val credentialStorage = CredentialStorage(this, securityManager)
        val repository = FileRepository(database.remoteServerDao(), credentialStorage)
        val viewModelFactory = ViewModelFactory(repository)

        setContent {
            MeldDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "file_manager") {
                        composable("file_manager") {
                            val viewModel: FileManagerViewModel = viewModel(factory = viewModelFactory)
                            FileManagerScreen(
                                viewModel = viewModel,
                                onOpenFile = { effect ->
                                    openFile(effect.fileItem, effect.serverId)
                                },
                                onShowToast = { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() },
                                onNavigateToAddStorage = { navController.navigate("add_storage") },
                                onNavigateToSettings = { navController.navigate("settings") },
                            )
                        }
                        composable("add_storage") {
                            val viewModel: AddStorageViewModel = viewModel(factory = viewModelFactory)
                            AddStorageScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onSuccess = { navController.popBackStack() },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
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
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast(".", "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    private val applicationId: String
        get() = packageName
}
