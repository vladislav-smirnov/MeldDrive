package io.github.airdaydreamers.melddrive.ui.components

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

object AdaptiveNavigation {
    /**
     * Defines the type of navigation layout to use based on available screen width.
     *
     * Compact (< 600dp): Modal drawer that slides over content (phone portrait)
     * Medium (600-840dp): Navigation rail alongside content (foldable unfolded, phone landscape)
     * Expanded (>= 840dp): Permanent full drawer alongside content (tablet, desktop)
     */
    enum class NavigationType {
        /** Modal drawer that slides over content — for compact screens (phones). */
        DRAWER,

        /** Navigation rail (icons + labels) shown alongside content — for medium screens (foldables). */
        RAIL,

        /** Permanent navigation drawer always visible — for expanded screens (tablets). */
        PERMANENT_DRAWER,
    }

    /**
     * Determines the appropriate [NavigationType] based on the current [WindowWidthSizeClass].
     *
     * This function is the single source of truth for adaptive navigation decisions.
     * Since [WindowWidthSizeClass] automatically recomputes on configuration changes
     * (fold/unfold, rotation, split-screen), the layout adapts in real-time.
     */
    fun calculateNavigationType(widthSizeClass: WindowWidthSizeClass): NavigationType = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> NavigationType.DRAWER
        WindowWidthSizeClass.Medium -> NavigationType.RAIL
        WindowWidthSizeClass.Expanded -> NavigationType.PERMANENT_DRAWER
        else -> NavigationType.DRAWER
    }
}
