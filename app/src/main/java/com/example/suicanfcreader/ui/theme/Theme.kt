package com.example.suicanfcreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.suicanfcreader.model.AppThemeMode

private val WhiteColorScheme = lightColorScheme(
    primary = SuicaGreen40,
    secondary = RailBlue40,
    tertiary = TicketCoral40,
    background = Mist,
    surface = Paper,
    surfaceVariant = Color(0xFFE7EFEC),
    onPrimary = Paper,
    onSecondary = Paper,
    onTertiary = Paper,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF53615E),
    outline = Color(0xFFC6D2CE)
)

private val DarkColorScheme = darkColorScheme(
    primary = SuicaGreen80,
    secondary = RailBlue80,
    tertiary = TicketCoral80,
    background = Night,
    surface = NightSurface,
    surfaceVariant = NightSurfaceVariant,
    onPrimary = Color(0xFF062C26),
    onSecondary = Color(0xFF082E3B),
    onTertiary = Color(0xFF4A1B0B),
    onBackground = Color(0xFFE7F0ED),
    onSurface = Color(0xFFE7F0ED),
    onSurfaceVariant = Color(0xFFB4C4BF),
    outline = Color(0xFF34433F)
)

private val AmoledColorScheme = darkColorScheme(
    primary = SuicaGreen80,
    secondary = RailBlue80,
    tertiary = TicketCoral80,
    background = AmoledBlack,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onPrimary = Color(0xFF062C26),
    onSecondary = Color(0xFF082E3B),
    onTertiary = Color(0xFF4A1B0B),
    onBackground = Color(0xFFF2F8F6),
    onSurface = Color(0xFFF2F8F6),
    onSurfaceVariant = Color(0xFFB9C8C3),
    outline = Color(0xFF252525)
)

@Composable
fun SuicaNFCReaderTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.AMOLED -> AmoledColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.WHITE -> WhiteColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
