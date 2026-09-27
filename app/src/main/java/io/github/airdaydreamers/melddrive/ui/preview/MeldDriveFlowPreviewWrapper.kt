package io.github.airdaydreamers.melddrive.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.airdaydreamers.melddrive.ui.components.AdaptiveNavigation.NavigationType
import io.github.airdaydreamers.melddrive.ui.theme.MeldDriveTheme

enum class PreviewScreen {
    FILE_MANAGER,
    SETTINGS,
    ADD_STORAGE,
}

/**
 * Multi-Screen Interactive Flow Simulator for MeldDrive.
 * Allows interactive navigation and screen transitions inside Android Studio
 * Interactive Preview Mode without requiring Hilt, ViewModels, or NavController.
 */
@Composable
fun MeldDriveFlowPreviewWrapper(initialScreen: PreviewScreen = PreviewScreen.FILE_MANAGER, navigationType: NavigationType? = null) {
    var currentScreen by remember { mutableStateOf(initialScreen) }

    MeldDriveTheme {
        when (currentScreen) {
            PreviewScreen.FILE_MANAGER -> {
                FileManagerPreviewWrapper(
                    navigationType = navigationType,
                    onNavigateToSettings = { currentScreen = PreviewScreen.SETTINGS },
                    onNavigateToAddStorage = { currentScreen = PreviewScreen.ADD_STORAGE },
                )
            }

            PreviewScreen.SETTINGS -> {
                SettingsPreviewWrapper(
                    onBack = { currentScreen = PreviewScreen.FILE_MANAGER },
                )
            }

            PreviewScreen.ADD_STORAGE -> {
                AddStoragePreviewWrapper(
                    onBack = { currentScreen = PreviewScreen.FILE_MANAGER },
                    onSuccess = { currentScreen = PreviewScreen.FILE_MANAGER },
                )
            }
        }
    }
}

@DevicePreviews
@ThemePreviews
@Composable
fun MeldDriveFlowInteractivePreview() {
    MeldDriveFlowPreviewWrapper()
}
