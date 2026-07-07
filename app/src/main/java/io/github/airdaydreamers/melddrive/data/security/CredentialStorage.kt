package io.github.airdaydreamers.melddrive.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "credentials")

class CredentialStorage(private val context: Context, private val securityManager: SecurityManager) {

    suspend fun saveCredentials(serverId: Long, username: String?, password: String?) {
        val keyUser = stringPreferencesKey("user_$serverId")
        val keyPass = stringPreferencesKey("pass_$serverId")
        context.dataStore.edit { preferences ->
            if (username != null) {
                preferences[keyUser] = securityManager.encrypt(username)
            } else {
                preferences.remove(keyUser)
            }
            if (password != null) {
                preferences[keyPass] = securityManager.encrypt(password)
            } else {
                preferences.remove(keyPass)
            }
        }
    }

    suspend fun getUsername(serverId: Long): String? {
        val key = stringPreferencesKey("user_$serverId")
        val encrypted = context.dataStore.data.map { preferences ->
            preferences[key]
        }.first()

        return encrypted?.let {
            securityManager.decrypt(it)
        }
    }

    suspend fun getPassword(serverId: Long): String? {
        val key = stringPreferencesKey("pass_$serverId")
        val encrypted = context.dataStore.data.map { preferences ->
            preferences[key]
        }.first()

        return encrypted?.let {
            securityManager.decrypt(it)
        }
    }

    suspend fun removeCredentials(serverId: Long) {
        val keyUser = stringPreferencesKey("user_$serverId")
        val keyPass = stringPreferencesKey("pass_$serverId")
        context.dataStore.edit { preferences ->
            preferences.remove(keyUser)
            preferences.remove(keyPass)
        }
    }
}
