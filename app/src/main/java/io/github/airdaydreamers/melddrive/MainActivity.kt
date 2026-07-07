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
import io.github.airdaydreamers.melddrive.ui.screens.AddStorageScreen
import io.github.airdaydreamers.melddrive.ui.screens.FileManagerScreen
import io.github.airdaydreamers.melddrive.ui.theme.MeldDriveTheme
import io.github.airdaydreamers.melddrive.ui.viewmodel.ViewModelFactory
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        val database = AppDatabase.getDatabase(this)
        val repository = FileRepository(database.remoteServerDao())
        val viewModelFactory = ViewModelFactory(repository)

        setContent {
            MeldDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "file_manager") {
                        composable("file_manager") {
                            FileManagerScreen(
                                viewModel = viewModel(factory = viewModelFactory),
                                onOpenFile = { openFile(it) },
                                onShowToast = { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() },
                                onNavigateToAddStorage = { navController.navigate("add_storage") }
                            )
                        }
                        composable("add_storage") {
                            AddStorageScreen(
                                viewModel = viewModel(factory = viewModelFactory),
                                onBack = { navController.popBackStack() },
                                onSuccess = { navController.popBackStack() }
                            )
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

    private fun openFile(fileItem: FileItem) {
        if (fileItem.storageType != StorageType.LOCAL) {
            Toast.makeText(this, "Remote file opening not implemented yet", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(fileItem.path)
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationId}.provider",
            file
        )
        val mimeType = getMimeType(file)

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

    private fun getMimeType(file: File): String {
        val extension = file.extension
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    private val applicationId: String
        get() = packageName
}
