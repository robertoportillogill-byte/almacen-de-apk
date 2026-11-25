package app.presupuesto.logica

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Convierte texto de importe a céntimos.
 * Acepta "1.234,56", "1,234.56", "1234,5", "1234.56", "1.500" (miles), "12,50 €".
 * Devuelve null si no es un número válido o es <= 0.
 */
fun parseImporte(raw: String): Long? {
    var s = raw.replace("€", "").replace("eur", "", ignoreCase = true).trim().replace(" ", "")
    if (s.isEmpty()) return null

    if (s.contains(',') && s.contains('.')) {
        // El último separador es el decimal; el otro es de miles.
        val pos = maxOf(s.lastIndexOf(','), s.lastIndexOf('.'))
        s = s.substring(0, pos).replace(".", "").replace(",", "") + "." + s.substring(pos + 1)
    } else if (s.contains(',')) {
        s = if (s.count { it == ',' } == 1 && s.substringAfter(',').length <= 2) s.replace(',', '.')
        else s.replace(",", "")
    } else if (s.contains('.')) {
        s = if (s.count { it == '.' } > 1 || s.substringAfter('.').length == 3) s.replace(".", "") else s
    }

    val valor = s.toBigDecimalOrNull() ?: return null
    if (valor <= BigDecimal.ZERO) return null
    return valor.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

/** 125050 -> "1.250,50" (sin símbolo), para rellenar el formulario. */
fun formatearImporte(cent: Long): String {
    val entero = cent / 100
    val decimales = cent % 100
    val miles = entero.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$miles,%02d".format(decimales)
}
