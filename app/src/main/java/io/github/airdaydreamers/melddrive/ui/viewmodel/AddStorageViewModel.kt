package io.github.airdaydreamers.melddrive.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.airdaydreamers.melddrive.R
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.model.StorageException
import io.github.airdaydreamers.melddrive.data.repository.ServerRepository
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageIntent
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class AddStorageViewModel @Inject constructor(private val serverRepository: ServerRepository, @ApplicationContext private val context: Context) : ViewModel() {
    private val _state = MutableStateFlow(AddStorageState())
    val state = _state.asStateFlow()

    fun onIntent(intent: AddStorageIntent) {
        Timber.d("AddStorageViewModel: Handling intent %s", intent::class.simpleName)
        when (intent) {
            is AddStorageIntent.ServerTypeChange -> _state.update {
                it.copy(serverType = intent.value, port = intent.value.defaultPort.toString())
            }

            is AddStorageIntent.DisplayNameChange -> _state.update { it.copy(displayName = intent.value) }

            is AddStorageIntent.HostChange -> _state.update { it.copy(host = intent.value) }

            is AddStorageIntent.PortChange -> _state.update { it.copy(port = intent.value) }

            is AddStorageIntent.UsernameChange -> _state.update { it.copy(username = intent.value) }

            is AddStorageIntent.PasswordChange -> _state.update { it.copy(password = intent.value) }

            is AddStorageIntent.AnonymousChange -> _state.update { it.copy(isAnonymous = intent.value) }

            is AddStorageIntent.TrustSelfSignedChange -> _state.update { it.copy(trustSelfSigned = intent.value) }

            AddStorageIntent.SaveServer -> saveServer()
        }
    }

    private fun saveServer() {
        val s = _state.value
        if (s.host.isBlank() || s.displayName.isBlank()) {
            _state.update { it.copy(error = context.getString(R.string.error_host_display_mandatory)) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val server = buildRemoteServer(s)
                Timber.d(
                    "AddStorageViewModel: Saving server type=%s host='%s' port=%d isAnon=%b trustSelfSigned=%b",
                    server.type,
                    server.host,
                    server.port,
                    server.isAnonymous,
                    server.trustSelfSigned,
                )
                serverRepository.addRemoteServer(server, if (s.isAnonymous) null else s.password)
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: StorageException) {
                Timber.e(e, "AddStorageViewModel: saveServer StorageException")
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: IOException) {
                Timber.e(e, "AddStorageViewModel: saveServer IOException")
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun buildRemoteServer(s: AddStorageState): RemoteServer = RemoteServer(
        displayName = s.displayName,
        host = s.host,
        port = s.port.toIntOrNull() ?: s.serverType.defaultPort,
        username = if (s.isAnonymous) null else s.username,
        password = null,
        isAnonymous = s.isAnonymous,
        type = s.serverType.name,
        trustSelfSigned = s.trustSelfSigned,
    )
}
