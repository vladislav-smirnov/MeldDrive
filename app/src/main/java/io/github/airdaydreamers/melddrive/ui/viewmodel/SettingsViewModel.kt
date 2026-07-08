package io.github.airdaydreamers.melddrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.airdaydreamers.melddrive.data.settings.SettingsManager
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsEffect
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsIntent
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        settingsManager.bufferSizeFlow.onEach { size ->
            _state.update { it.copy(bufferSizeMb = size) }
        }.launchIn(viewModelScope)

        settingsManager.isAggressiveBufferingEnabledFlow.onEach { enabled ->
            _state.update { it.copy(isAggressiveBufferingEnabled = enabled) }
        }.launchIn(viewModelScope)
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetBufferSize -> {
                viewModelScope.launch {
                    settingsManager.setBufferSize(intent.sizeMb)
                }
            }

            is SettingsIntent.SetAggressiveBufferingEnabled -> {
                viewModelScope.launch {
                    settingsManager.setAggressiveBufferingEnabled(intent.enabled)
                }
            }

            SettingsIntent.NavigateBack -> {
                viewModelScope.launch {
                    _effect.send(SettingsEffect.NavigateBack)
                }
            }
        }
    }
}
