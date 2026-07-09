package io.github.airdaydreamers.melddrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.airdaydreamers.melddrive.data.settings.SettingsManager
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsEffect
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsIntent
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsState
import io.github.airdaydreamers.melddrive.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                SettingsEffect.NavigateBack -> onBack()
            }
        }
    }

    SettingsContent(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(SettingsIntent.NavigateBack) }) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StreamingSettings(state, onIntent)
        }
    }
}

@Composable
private fun StreamingSettings(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    Text(
        text = "Streaming & Buffering",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BufferSizeSetting(state.bufferSizeMb, onIntent)

            HorizontalDivider()

            AggressiveBufferingSetting(state.isAggressiveBufferingEnabled, onIntent)
        }
    }
}

@Composable
private fun BufferSizeSetting(bufferSizeMb: Int, onIntent: (SettingsIntent) -> Unit) {
    Column {
        Text(
            text = "Buffer Size: $bufferSizeMb MB",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Larger buffers improve stability but use more memory.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = bufferSizeMb.toFloat(),
            onValueChange = { onIntent(SettingsIntent.SetBufferSize(it.toInt())) },
            valueRange = SettingsManager.MIN_BUFFER_SIZE_MB.toFloat()..SettingsManager.MAX_BUFFER_SIZE_MB.toFloat(),
            steps = 99,
        )
    }
}

@Composable
private fun AggressiveBufferingSetting(isEnabled: Boolean, onIntent: (SettingsIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Aggressive Buffering",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Prefetch upcoming data in the background. Best for unstable connections.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { onIntent(SettingsIntent.SetAggressiveBufferingEnabled(it)) },
        )
    }
}
