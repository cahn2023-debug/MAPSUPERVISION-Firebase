package com.mapsupervision.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

@Immutable
data class MapSupervisionExtendedColors(
    val panelBackground: Color,
    val panelBackgroundAlt: Color,
    val panelBackgroundOverlay: Color,
    val accent: Color,
    val success: Color,
    val mapAccent: Color,
    val warning: Color,
    val warningSoft: Color,
    val info: Color,
    val danger: Color,
    val dangerSoft: Color
)

internal val LocalMapSupervisionExtendedColors = staticCompositionLocalOf {
    MapSupervisionExtendedColors(
        panelBackground = SurfaceDark,
        panelBackgroundAlt = SurfaceVariantDark,
        panelBackgroundOverlay = SurfaceOverlayDark,
        accent = PrimaryContainer,
        success = SuccessColor,
        mapAccent = MapAccentColor,
        warning = WarningColor,
        warningSoft = WarningSoftColor,
        info = InfoColor,
        danger = DangerColor,
        dangerSoft = DangerSoftColor
    )
}

val MaterialTheme.extendedColors: MapSupervisionExtendedColors
    @Composable
    get() = LocalMapSupervisionExtendedColors.current
