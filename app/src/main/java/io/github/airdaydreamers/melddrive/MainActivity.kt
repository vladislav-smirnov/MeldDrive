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
import io.github.airdaydreamers.melddrive.data.model.FileItem
import io.github.airdaydreamers.melddrive.ui.screens.FileManagerScreen
import io.github.airdaydreamers.melddrive.ui.theme.MeldDriveTheme
import java.io.File
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()

        setContent {
            MeldDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileManagerScreen(
                        onOpenFile = { openFile(it) },
                        onShowToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                    )
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
        val file = fileItem.path.toFile()
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
