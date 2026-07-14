package io.github.airdaydreamers.melddrive.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MeldDriveKey : NavKey

@Serializable
data object FileManager : MeldDriveKey

@Serializable
data object AddStorage : MeldDriveKey

@Serializable
data object Settings : MeldDriveKey
