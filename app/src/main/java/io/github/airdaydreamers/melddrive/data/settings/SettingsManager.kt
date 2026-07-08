package io.github.airdaydreamers.melddrive.data.settings

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

    companion object {
        private val BUFFER_SIZE_KEY = intPreferencesKey("buffer_size")
        private val AGGRESSIVE_BUFFERING_KEY = booleanPreferencesKey("aggressive_buffering")

        const val DEFAULT_BUFFER_SIZE_MB = 8
        const val MAX_BUFFER_SIZE_MB = 100
        const val MIN_BUFFER_SIZE_MB = 1
    }

    val bufferSizeFlow: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[BUFFER_SIZE_KEY] ?: DEFAULT_BUFFER_SIZE_MB
    }

    val isAggressiveBufferingEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[AGGRESSIVE_BUFFERING_KEY] ?: false
    }

    suspend fun setBufferSize(sizeMb: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[BUFFER_SIZE_KEY] = sizeMb.coerceIn(MIN_BUFFER_SIZE_MB, MAX_BUFFER_SIZE_MB)
        }
    }

    suspend fun setAggressiveBufferingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AGGRESSIVE_BUFFERING_KEY] = enabled
        }
    }
}
