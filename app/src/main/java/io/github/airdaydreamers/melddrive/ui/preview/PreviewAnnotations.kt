package io.github.airdaydreamers.melddrive.ui.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/**
 * Default device preview used to keep preview rendering lightweight.
 *
 * Use [AllDevicePreviews] when validating every supported device profile.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Phone - Compact", group = "Devices", device = Devices.PHONE, showBackground = true)
annotation class DevicePreviews

/**
 * Multi-preview annotation for all supported device profiles and screen sizes.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Phone - Compact", group = "Devices", device = Devices.PHONE, showBackground = true)
@Preview(name = "Tablet - Medium", group = "Devices", device = Devices.TABLET, showBackground = true)
@Preview(name = "Foldable - Expanded", group = "Devices", device = Devices.FOLDABLE, showBackground = true)
@Preview(
    name = "Landscape / VR",
    group = "Devices",
    device = "spec:width=1920dp,height=1080dp,dpi=320,orientation=landscape",
    showBackground = true,
)
annotation class AllDevicePreviews

/**
 * Default theme preview used to keep preview rendering lightweight.
 *
 * Use [AllThemePreviews] when validating both supported themes.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Dark Theme", group = "Themes", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class ThemePreviews

/**
 * Multi-preview annotation for all supported UI themes.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Light Theme", group = "Themes", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dark Theme", group = "Themes", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class AllThemePreviews
