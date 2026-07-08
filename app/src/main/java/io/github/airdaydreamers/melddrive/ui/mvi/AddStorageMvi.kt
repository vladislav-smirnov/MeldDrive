package io.github.airdaydreamers.melddrive.ui.mvi

data class AddStorageState(
    val displayName: String = "",
    val host: String = "",
    val port: String = "445",
    val username: String = "",
    val password: String = "",
    val isAnonymous: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
)

sealed interface AddStorageIntent {
    data class DisplayNameChange(val value: String) : AddStorageIntent
    data class HostChange(val value: String) : AddStorageIntent
    data class PortChange(val value: String) : AddStorageIntent
    data class UsernameChange(val value: String) : AddStorageIntent
    data class PasswordChange(val value: String) : AddStorageIntent
    data class AnonymousChange(val value: Boolean) : AddStorageIntent
    data object SaveServer : AddStorageIntent
}

sealed interface AddStorageEffect {
    data object NavigateBack : AddStorageEffect
}
