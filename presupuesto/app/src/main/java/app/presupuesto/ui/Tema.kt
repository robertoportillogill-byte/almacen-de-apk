package app.presupuesto.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

private val Claro = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF0E7490),
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    background = Color(0xFFF2F6F5),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE7EFEC),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    error = Color(0xFFBE123C)
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF67E8F9),
    secondaryContainer = Color(0xFF155E75),
    onSecondaryContainer = Color(0xFFCFFAFE),
    background = Color(0xFF0B1514),
    onBackground = Color(0xFFE2F1EE),
    surface = Color(0xFF131F1E),
    onSurface = Color(0xFFE2F1EE),
    surfaceVariant = Color(0xFF1E2F2D),
    onSurfaceVariant = Color(0xFFA7BDB9),
    outline = Color(0xFF5B716E),
    error = Color(0xFFFB7185)
)

@Composable
fun PresupuestoTema(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        content = content
    )
}

val FormaTarjeta: Shape = RoundedCornerShape(20.dp)

@Composable
fun colorIngreso(): Color = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF15803D)

@Composable
fun colorEgreso(): Color = if (isSystemInDarkTheme()) Color(0xFFFB7185) else Color(0xFFBE123C)

@Composable
fun fondoCabecera(): Brush = if (isSystemInDarkTheme()) {
    Brush.linearGradient(listOf(Color(0xFF115E59), Color(0xFF164E63)))
} else {
    Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF0E7490)))
}

private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "ES"))

/** Céntimos -> "1.234,56 €" */
fun moneda(cent: Long): String = formatoMoneda.format(cent / 100.0)
