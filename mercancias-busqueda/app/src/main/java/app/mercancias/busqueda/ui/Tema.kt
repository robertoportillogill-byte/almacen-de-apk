package app.mercancias.busqueda.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Verde = Color(0xFF2E9E5B)
val Naranja = Color(0xFFD9730D)

private val Claro = lightColorScheme(
    primary = Color(0xFF0B5CAD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF545F71),
    secondaryContainer = Color(0xFFD8E3F8),
    tertiary = Verde,
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFE0E2EC),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468B),
    onPrimaryContainer = Color(0xFFD5E3FF),
    tertiary = Color(0xFF7BDA9C),
)

@Composable
fun Tema(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = contenido)
}
