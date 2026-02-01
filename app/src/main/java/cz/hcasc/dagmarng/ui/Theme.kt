package cz.hcasc.dagmarng.ui

import androidx.compose.material3.MaterialTheme
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

@Composable
fun DagmarNgTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Light,
        content = content
    )
}
