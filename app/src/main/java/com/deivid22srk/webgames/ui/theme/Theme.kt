package com.deivid22srk.webgames.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.deivid22srk.webgames.data.AppSettings

private val DeepPurple = Color(0xFF7C6CFF)
private val DeepPurpleDark = Color(0xFFB7A9FF)
private val VoidBg = Color(0xFF17124A)
private val VoidBgDark = Color(0xFF0E0B24)
private val Mint = Color(0xFF4ECDC4)
private val Sunny = Color(0xFFFFD166)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = DeepPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E1FF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Mint,
    onSecondary = Color(0xFF003734),
    tertiary = Sunny,
    onTertiary = Color(0xFF402D00),
    background = Color(0xFFFCF9FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFCF9FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE6E0F4),
    onSurfaceVariant = Color(0xFF484554),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = DeepPurpleDark,
    onPrimary = Color(0xFF2A1A6B),
    primaryContainer = Color(0xFF42309B),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Mint,
    onSecondary = Color(0xFF00201E),
    tertiary = Sunny,
    onTertiary = Color(0xFF402D00),
    background = VoidBgDark,
    onBackground = Color(0xFFE5E1F4),
    surface = VoidBgDark,
    onSurface = Color(0xFFE5E1F4),
    surfaceVariant = Color(0xFF2C2842),
    onSurfaceVariant = Color(0xFFC9C3DE),
)

/**
 * Tema Material 3 Expressive com:
 * - Dynamic Color (Material You) no Android 12+ quando habilitado;
 * - fallback com paleta própria (deep purple / mint / sunny);
 * - MotionScheme expressivo (molas).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebGamesTheme(
    settings: AppSettings?,
    content: @Composable () -> Unit,
) {
    val dark = when (settings?.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val useDynamic = settings?.dynamicColor ?: true

    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content,
    )
}
