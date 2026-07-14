package io.github.airdaydreamers.melddrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.airdaydreamers.melddrive.data.storage.SettingsManager
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsIntent
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        settingsManager.bufferingEnabled,
        settingsManager.bufferSizeMb,
    ) { enabled, size ->
        SettingsState(bufferingEnabled = enabled, bufferSizeMb = size)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SHARING_TIMEOUT),
        initialValue = SettingsState(),
    )

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetBufferingEnabled -> {
                viewModelScope.launch {
                    settingsManager.setBufferingEnabled(intent.enabled)
                }
            }

            is SettingsIntent.SetBufferSizeMb -> {
                viewModelScope.launch {
                    settingsManager.setBufferSizeMb(intent.sizeMb)
                }
            }
        }
    }

    companion object {
        private const val SHARING_TIMEOUT = 5000L
    }
}
