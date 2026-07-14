package io.github.airdaydreamers.melddrive.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    val bufferingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[KEY_BUFFERING_ENABLED] ?: false
    }

    val bufferSizeMb: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[KEY_BUFFER_SIZE_MB] ?: DEFAULT_BUFFER_SIZE_MB
    }

    suspend fun setBufferingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BUFFERING_ENABLED] = enabled
        }
    }

    suspend fun setBufferSizeMb(sizeMb: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[KEY_BUFFER_SIZE_MB] = sizeMb
        }
    }

    companion object {
        private val KEY_BUFFERING_ENABLED = booleanPreferencesKey("buffering_enabled")
        private val KEY_BUFFER_SIZE_MB = intPreferencesKey("buffer_size_mb")
        const val DEFAULT_BUFFER_SIZE_MB = 16
    }
}
