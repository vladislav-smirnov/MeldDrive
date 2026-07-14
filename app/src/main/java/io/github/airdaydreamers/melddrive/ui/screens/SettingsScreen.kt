package io.github.airdaydreamers.melddrive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.airdaydreamers.melddrive.R
import io.github.airdaydreamers.melddrive.data.model.AppLanguage
import io.github.airdaydreamers.melddrive.ui.components.SelectLanguageBottomSheetComponent
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
    var showLanguageBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            LanguageSelectionSection(
                currentLanguageCode = state.currentLanguageCode,
                onClick = { showLanguageBottomSheet = true },
            )

            BufferingToggleSection(
                bufferingEnabled = state.bufferingEnabled,
                onCheckedChange = { onIntent(SettingsIntent.SetBufferingEnabled(it)) },
            )

            BufferSizeSection(
                bufferingEnabled = state.bufferingEnabled,
                bufferSizeMb = state.bufferSizeMb,
                onValueChange = { onIntent(SettingsIntent.SetBufferSizeMb(it)) },
            )
        }

        if (showLanguageBottomSheet) {
            SelectLanguageBottomSheetComponent(
                currentLanguageCode = state.currentLanguageCode,
                onDismissRequest = { showLanguageBottomSheet = false },
                onLanguageSelected = { selectedCode ->
                    onIntent(SettingsIntent.SetLanguage(selectedCode))
                    showLanguageBottomSheet = false
                },
            )
        }
    }
}

@Composable
fun LanguageSelectionSection(currentLanguageCode: String, onClick: () -> Unit) {
    val currentLanguageName = remember(currentLanguageCode) {
        AppLanguage.supportedLanguages.find {
            it.code == currentLanguageCode || (it.code == "id" && currentLanguageCode == "in")
        }?.displayName ?: "English"
    }

    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.settings_language_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                text = currentLanguageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("current_language_display_text"),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("language_selection_row"),
    )
}

@Composable
fun BufferingToggleSection(bufferingEnabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.settings_enable_buffering_title, stringResource(R.string.untranslatable_smb)),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.settings_enable_buffering_summary, stringResource(R.string.untranslatable_smb)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = bufferingEnabled,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag("buffering_switch"),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(CircleShape)
            .clickable { onCheckedChange(!bufferingEnabled) }
            .testTag("buffering_toggle_row"),
    )
}

@Composable
fun BufferSizeSection(bufferingEnabled: Boolean, bufferSizeMb: Int, onValueChange: (Int) -> Unit) {
    // Show a card with buffer size controls only when buffering is enabled.
    AnimatedVisibility(
        visible = bufferingEnabled,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_buffer_size_title, bufferSizeMb, stringResource(R.string.untranslatable_mb)),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = stringResource(R.string.settings_buffer_size_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Slider(
                        value = bufferSizeMb.toFloat(),
                        onValueChange = { onValueChange(it.toInt()) },
                        valueRange = MIN_BUFFER_MB..MAX_BUFFER_MB,
                        steps = BUFFER_STEPS,
                        modifier = Modifier.fillMaxWidth().testTag("buffer_size_slider"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.mb_format, MIN_BUFFER_MB.toInt(), stringResource(R.string.untranslatable_mb)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(R.string.mb_format, MAX_BUFFER_MB.toInt(), stringResource(R.string.untranslatable_mb)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
