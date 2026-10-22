package com.punyplatipus.podouvir.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = RedLight,
    onPrimaryContainer = BrandRedDark,
    secondary = BrandAmber,
    onSecondary = Color.Black,
    secondaryContainer = YellowLight,
    onSecondaryContainer = Color(0xFF3E2D00),
    tertiary = BrandOrange,
    onTertiary = Color.White,
    background = WarmSurfaceLight,
    surface = WarmSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = RedLight,
    onPrimary = Color(0xFF690005),
    primaryContainer = BrandRedDark,
    onPrimaryContainer = RedLight,
    secondary = BrandYellow,
    onSecondary = Color(0xFF3E2D00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = YellowLight,
    tertiary = OrangeLight,
    onTertiary = Color(0xFF4A1800),
    background = WarmSurfaceDark,
    surface = WarmSurfaceDark,
)

/** Brand red/yellow palette by default; wallpaper-based dynamic color is opt-in (Android 12+). */
@Composable
fun PodOuvirTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
