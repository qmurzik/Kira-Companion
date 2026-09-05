package com.kira.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.kira.companion.model.AppTheme

private val LightColors = lightColorScheme(
    primary = KiraPurple60,
    onPrimary = KiraSurfaceLight,
    secondary = KiraPink40,
    onSecondary = KiraSurfaceLight,
    background = KiraBgLight,
    onBackground = KiraOnLight,
    surface = KiraSurfaceLight,
    onSurface = KiraOnLight,
    surfaceVariant = KiraPurple10,
    onSurfaceVariant = KiraPurple60,
    tertiary = KiraPink80,
)

private val DarkColors = darkColorScheme(
    primary = KiraPurple40,
    onPrimary = KiraBgDark,
    secondary = KiraPink40,
    onSecondary = KiraBgDark,
    background = KiraBgDark,
    onBackground = KiraOnDark,
    surface = KiraSurfaceDark,
    onSurface = KiraOnDark,
    surfaceVariant = KiraPurple90,
    onSurfaceVariant = KiraPurple10,
    tertiary = KiraPink40,
)

@Composable
fun KiraCompanionTheme(appTheme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val useDark = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val colors = if (useDark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = KiraTypography,
        content = content,
    )
}
