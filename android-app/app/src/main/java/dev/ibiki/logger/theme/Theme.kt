package dev.ibiki.logger.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB5E5D3), onPrimary = Color(0xFF15392D),
    secondary = Color(0xFFC5BEEC), onSecondary = Color(0xFF332D4C),
    background = Color(0xFF0E171C), onBackground = Color(0xFFE7ECEC),
    surface = Color(0xFF17232A), onSurface = Color(0xFFE7ECEC),
    surfaceVariant = Color(0xFF26343C), onSurfaceVariant = Color(0xFFA9B9BD),
    outline = Color(0xFF4D5D63), error = Color(0xFFFFB4A9)
)

@Composable
fun IbikiLoggerTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
