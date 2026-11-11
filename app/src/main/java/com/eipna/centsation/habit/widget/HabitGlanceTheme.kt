package com.eipna.centsation.habit.widget

import android.content.Context
import android.content.res.Configuration
import android.os.PowerManager
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import com.eipna.centsation.data.Theme
import com.eipna.centsation.util.PreferenceUtil

object HabitGlanceTheme {

    val LightScheme = lightColorScheme(
        primary = Color(0xFF00875A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD1F4E0),
        onPrimaryContainer = Color(0xFF003822),
        secondary = Color(0xFF6366F1),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E7FF),
        onSecondaryContainer = Color(0xFF1E1B4B),
        tertiary = Color(0xFFF59E0B),
        onTertiary = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFF94A3B8),
        error = Color(0xFFEF4444)
    )

    val DarkScheme = darkColorScheme(
        primary = Color(0xFF00E676),
        onPrimary = Color(0xFF003822),
        primaryContainer = Color(0xFF005234),
        onPrimaryContainer = Color(0xFF7CFFB7),
        secondary = Color(0xFF818CF8),
        onSecondary = Color(0xFF1E1B4B),
        secondaryContainer = Color(0xFF312E81),
        onSecondaryContainer = Color(0xFFC7D2FE),
        tertiary = Color(0xFFFBBF24),
        onTertiary = Color(0xFF451A03),
        surface = Color(0xFF111620),
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = Color(0xFF1E2533),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF475569),
        error = Color(0xFFF87171)
    )

    val Colors: ColorProviders = ColorProviders(
        light = LightScheme,
        dark = DarkScheme
    )

    fun resolveDarkMode(context: Context): Boolean {
        val prefs = PreferenceUtil(context)
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return when (prefs.theme) {
            Theme.DARK.VALUE -> true
            Theme.LIGHT.VALUE -> false
            Theme.BATTERY.VALUE -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                (pm?.isPowerSaveMode == true) || systemDark
            }
            else -> systemDark
        }
    }

    fun colorsFor(context: Context): ColorProviders {
        val prefs = PreferenceUtil(context)
        return when (prefs.theme) {
            Theme.DARK.VALUE -> ColorProviders(light = DarkScheme, dark = DarkScheme)
            Theme.LIGHT.VALUE -> ColorProviders(light = LightScheme, dark = LightScheme)
            else -> Colors
        }
    }
}
