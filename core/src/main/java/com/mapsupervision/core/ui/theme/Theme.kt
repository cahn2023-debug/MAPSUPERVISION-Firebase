package com.mapsupervision.core.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MidnightSlate = darkColorScheme(
    primary = PrimaryPeach,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    
    secondary = SecondaryMint,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    
    tertiary = TertiaryCyan,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    
    background = BackgroundDark,
    onBackground = OnBackground,
    
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariant,
    
    outline = OutlineColor,
    outlineVariant = OutlineVariantColor,
    
    error = ErrorColor,
    onError = OnErrorColor,
    errorContainer = ErrorContainerColor,
    onErrorContainer = OnErrorContainerColor
)

@Suppress("DEPRECATION")
@Composable
fun MapSupervisionTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = MidnightSlate
    val extendedColors = MapSupervisionExtendedColors(
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
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                window.statusBarColor = android.graphics.Color.parseColor("#000000")
                window.navigationBarColor = android.graphics.Color.parseColor("#000000")
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    CompositionLocalProvider(LocalMapSupervisionExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
