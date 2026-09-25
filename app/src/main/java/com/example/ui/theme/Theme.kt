package com.example.ui.theme

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

private val LightColorScheme = lightColorScheme(
    primary = NovaPrimary,
    onPrimary = Color.White,
    primaryContainer = NovaPrimaryContainerLight,
    onPrimaryContainer = NovaOnPrimaryContainerLight,
    secondary = NovaSecondary,
    secondaryContainer = NovaSecondaryContainerLight,
    tertiary = NovaTertiary,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

private val DarkColorScheme = darkColorScheme(
    primary = NovaDarkPrimary,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    tertiary = Color(0xFFA78BFA),
    background = NovaDarkBackground,
    surface = NovaDarkSurface,
    surfaceVariant = NovaDarkSurfaceVariant,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569)
)

private val AmoledColorScheme = darkColorScheme(
    primary = NovaDarkPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E1B4B),
    onPrimaryContainer = Color(0xFFC7D2FE),
    secondary = Color(0xFF38BDF8),
    tertiary = Color(0xFFA78BFA),
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF27272A)
)

private val SepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBD9BF),
    onPrimaryContainer = Color(0xFF3D270C),
    secondary = Color(0xFF8C6D46),
    tertiary = Color(0xFF785427),
    background = SepiaBackground,
    surface = SepiaSurface,
    surfaceVariant = Color(0xFFEADFC9),
    onBackground = SepiaOnSurface,
    onSurface = SepiaOnSurface,
    onSurfaceVariant = Color(0xFF6B583E),
    outline = Color(0xFFD3C5AB)
)

@Composable
fun NovaPdfTheme(
    appThemeName: String = "System", // System, Light, Dark, AMOLED, Sepia
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (appThemeName) {
        "Light" -> LightColorScheme
        "Dark" -> DarkColorScheme
        "AMOLED" -> AmoledColorScheme
        "Sepia" -> SepiaColorScheme
        else -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColorScheme else LightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NovaPdfTheme(
        appThemeName = if (darkTheme) "Dark" else "Light",
        dynamicColor = dynamicColor,
        content = content
    )
}
