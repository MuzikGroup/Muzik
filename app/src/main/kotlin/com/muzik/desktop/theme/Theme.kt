package com.muzik.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.muzik.desktop.library.ThemeMode

// Muzik brand: deep violet with a warm soda-pink accent.
val MuzikPurple = Color(0xFF7C4DFF)
val MuzikPurpleBright = Color(0xFF9D7BFF)
val MuzikPink = Color(0xFFFF5C8A)
val MuzikDarkSurface = Color(0xFF101014)
val MuzikDarkSurfaceVariant = Color(0xFF1C1C22)
val MuzikLightSurface = Color(0xFFFDFCFF)
val MuzikLightSurfaceVariant = Color(0xFFF2EDF7)

private val DarkColors = darkColorScheme(
    primary = MuzikPurpleBright,
    onPrimary = Color.White,
    secondary = MuzikPink,
    tertiary = Color(0xFF64FFDA),
    background = MuzikDarkSurface,
    onBackground = Color(0xFFEDE7F6),
    surface = MuzikDarkSurface,
    onSurface = Color(0xFFEDE7F6),
    surfaceVariant = MuzikDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBDB6C9),
    outline = Color(0xFF494552),
)

private val LightColors = lightColorScheme(
    primary = MuzikPurple,
    onPrimary = Color.White,
    secondary = MuzikPink,
    tertiary = Color(0xFF00BFA5),
    background = MuzikLightSurface,
    onBackground = Color(0xFF1B1B1F),
    surface = MuzikLightSurface,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = MuzikLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF494552),
    outline = Color(0xFF7A7581),
)

private val MuzikTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MuzikTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MuzikTypography,
        content = content,
    )
}
