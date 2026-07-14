package io.github.airdaydreamers.melddrive.ui.viewmodel

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.airdaydreamers.melddrive.data.storage.SettingsManager
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsIntent
import io.github.airdaydreamers.melddrive.ui.mvi.SettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settingsManager: SettingsManager) : ViewModel() {

    private val currentLanguageCode = MutableStateFlow(getActiveLanguageCode())

    val state: StateFlow<SettingsState> = combine(
        settingsManager.bufferingEnabled,
        settingsManager.bufferSizeMb,
        currentLanguageCode,
    ) { enabled, size, lang ->
        SettingsState(
            bufferingEnabled = enabled,
            bufferSizeMb = size,
            currentLanguageCode = lang,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SHARING_TIMEOUT),
        initialValue = SettingsState(currentLanguageCode = getActiveLanguageCode()),
    )

    private fun getActiveLanguageCode(): String {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        return if (!currentLocales.isEmpty) {
            currentLocales.get(0)?.language ?: "en"
        } else {
            java.util.Locale.getDefault().language
        }
    }

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

            is SettingsIntent.SetLanguage -> {
                val locale = LocaleListCompat.forLanguageTags(intent.languageCode)
                Log.d("SettingsViewModel", "Setting language to ${intent.languageCode} with locale $locale")
                AppCompatDelegate.setApplicationLocales(
                    locale,
                )
                currentLanguageCode.value = intent.languageCode
            }
        }
    }

    companion object {
        private const val SHARING_TIMEOUT = 5000L
    }
}
