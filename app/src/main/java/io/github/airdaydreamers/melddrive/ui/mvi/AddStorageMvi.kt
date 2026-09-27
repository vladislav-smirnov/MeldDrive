package io.github.airdaydreamers.melddrive.ui.mvi

private const val DEFAULT_PORT_SMB = 445
private const val DEFAULT_PORT_WEBDAV = 80

enum class ServerType(val defaultPort: Int) {
    SMB(DEFAULT_PORT_SMB),
    WEBDAV(DEFAULT_PORT_WEBDAV),
}

data class AddStorageState(
    val serverType: ServerType = ServerType.SMB,
    val displayName: String = "",
    val host: String = "",
    val port: String = ServerType.SMB.defaultPort.toString(),
    val username: String = "",
    val password: String = "",
    val isAnonymous: Boolean = false,
    val trustSelfSigned: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
)

sealed interface AddStorageIntent {
    data class ServerTypeChange(val value: ServerType) : AddStorageIntent
    data class DisplayNameChange(val value: String) : AddStorageIntent
    data class HostChange(val value: String) : AddStorageIntent
    data class PortChange(val value: String) : AddStorageIntent
    data class UsernameChange(val value: String) : AddStorageIntent
    data class PasswordChange(val value: String) : AddStorageIntent
    data class AnonymousChange(val value: Boolean) : AddStorageIntent
    data class TrustSelfSignedChange(val value: Boolean) : AddStorageIntent
    data object SaveServer : AddStorageIntent
}
