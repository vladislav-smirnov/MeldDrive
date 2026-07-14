package io.github.airdaydreamers.melddrive.ui.mvi

data class SettingsState(val bufferingEnabled: Boolean = false, val bufferSizeMb: Int = 16)

sealed interface SettingsIntent {
    data class SetBufferingEnabled(val enabled: Boolean) : SettingsIntent
    data class SetBufferSizeMb(val sizeMb: Int) : SettingsIntent
}

sealed interface SettingsEffect {
    // Reactive flow, no side effects required for now
}
