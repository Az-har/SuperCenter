package com.eipna.centsation.habit.ui.theme

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
    primary = Color(0xFF1B6B50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2D1),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9D9),
    onSecondaryContainer = Color(0xFF082016),
    tertiary = Color(0xFF3F6374),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC3E8FD),
    onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFFBFDFA),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFBFDFA),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD5B6),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005137),
    onPrimaryContainer = Color(0xFFA6F2D1),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCEE9D9),
    tertiary = Color(0xFFA7CCE0),
    onTertiary = Color(0xFF0A3545),
    tertiaryContainer = Color(0xFF264C5C),
    onTertiaryContainer = Color(0xFFC3E8FD),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF8A938D)
)

@Composable
fun SuperCenterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
