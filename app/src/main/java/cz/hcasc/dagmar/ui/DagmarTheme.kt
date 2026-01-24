package cz.hcasc.dagmar

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0B2B40),
    onPrimary = Color.White,
    secondary = Color(0xFF1D6D8C),
    onSecondary = Color.White,
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF111418),
    surface = Color.White,
    onSurface = Color(0xFF111418)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7EC8E3),
    onPrimary = Color(0xFF06202F),
    secondary = Color(0xFF9AD7F0),
    onSecondary = Color(0xFF06202F),
    background = Color(0xFF0A0F14),
    onBackground = Color(0xFFE8EDF1),
    surface = Color(0xFF0F1720),
    onSurface = Color(0xFFE8EDF1)
)

@Composable
fun DagmarTheme(content: @Composable () -> Unit) {
    // Deterministická světla paleta (u WebView nejsou jasové kontrasty dle systému).
    MaterialTheme(
        colorScheme = Light,
        content = content
    )
}
