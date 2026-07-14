package io.github.airdaydreamers.melddrive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsIntent
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsState
import io.github.airdaydreamers.melddrive.ui.viewmodel.SettingsViewModel

private const val MIN_BUFFER_MB = 8f
private const val MAX_BUFFER_MB = 128f
private const val BUFFER_STEPS = 14

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(state: SettingsState, onIntent: (SettingsIntent) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            BufferingToggleSection(
                bufferingEnabled = state.bufferingEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetBufferingEnabled(it)) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            BufferSizeSection(
                bufferingEnabled = state.bufferingEnabled,
                bufferSizeMb = state.bufferSizeMb,
                onValueChange = { onIntent(SettingsIntent.SetBufferSizeMb(it)) },
            )
        }
    }
}

@Composable
fun BufferingToggleSection(bufferingEnabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Enable SMB Buffering",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Prefetch next chunks in parallel when playing/reading SMB files",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = bufferingEnabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun BufferSizeSection(bufferingEnabled: Boolean, bufferSizeMb: Int, onValueChange: (Int) -> Unit) {
    // Show a card with buffer size controls only when buffering is enabled.
    AnimatedVisibility(
        visible = bufferingEnabled,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Buffer Size: $bufferSizeMb MB",
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = "Larger buffers improve stability but use more memory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Slider(
                    value = bufferSizeMb.toFloat(),
                    onValueChange = { onValueChange(it.toInt()) },
                    valueRange = MIN_BUFFER_MB..MAX_BUFFER_MB,
                    steps = BUFFER_STEPS,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${MIN_BUFFER_MB.toInt()} MB", style = MaterialTheme.typography.bodySmall)
                    Text("${MAX_BUFFER_MB.toInt()} MB", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
