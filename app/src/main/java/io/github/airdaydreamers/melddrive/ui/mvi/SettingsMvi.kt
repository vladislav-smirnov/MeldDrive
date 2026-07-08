package io.github.airdaydreamers.melddrive.ui.mvi

import io.github.airdaydreamers.melddrive.data.settings.SettingsManager

data class SettingsState(val bufferSizeMb: Int = SettingsManager.DEFAULT_BUFFER_SIZE_MB, val isAggressiveBufferingEnabled: Boolean = false)

sealed interface SettingsIntent {
    data class SetBufferSize(val sizeMb: Int) : SettingsIntent
    data class SetAggressiveBufferingEnabled(val enabled: Boolean) : SettingsIntent
    data object NavigateBack : SettingsIntent
}

sealed interface SettingsEffect {
    data object NavigateBack : SettingsEffect
}
