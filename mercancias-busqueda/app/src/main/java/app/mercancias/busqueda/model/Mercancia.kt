package app.mercancias.busqueda.model

import kotlinx.serialization.Serializable
import java.text.Normalizer

enum class Estado { PENDIENTE, PARCIAL, ENCONTRADO }

/**
 * Una línea de la hoja de ruta.
 *
 * Bultos "2/1" = [bultosTotal] 2 en total, pero solo hay que llevar [bultosLlevar] 1.
 * Lo que se busca (y lo que cuenta para darla por encontrada) es [bultosLlevar].
 */
@Serializable
data class Mercancia(
    val id: Long = 0,
    val orden: Int = 0,
    val numOrden: String = "",
    val cliente: String = "",
    val poblacion: String = "",
    val direccion: String = "",
    val bultosTotal: Int = 1,
    val bultosLlevar: Int = 1,
    val kilos: Double = 0.0,
    val encontrados: Int = 0,
    val ubicacion: String = "",
    val observacion: String = "",
    /** Dato leído con dudas (OCR o voz): conviene revisarlo. */
    val revisar: Boolean = false,
) {
    val estado: Estado
        get() = when {
            encontrados >= bultosLlevar -> Estado.ENCONTRADO
            encontrados > 0 -> Estado.PARCIAL
            else -> Estado.PENDIENTE
        }

    val faltan: Int get() = (bultosLlevar - encontrados).coerceAtLeast(0)

    val bultosTexto: String get() = "$bultosTotal/$bultosLlevar"

    /** Aviso automático cuando solo se ha encontrado una parte. */
    val avisoFaltan: String?
        get() = if (estado == Estado.PARCIAL) {
            "Faltan $faltan de $bultosLlevar bultos (encontrados $encontrados)"
        } else null

    val direccionCompleta: String
        get() = listOf(direccion, poblacion).filter { it.isNotBlank() }.joinToString(" · ")
}

enum class Campo(val etiqueta: String) {
    ORDEN("Orden"),
    NUM_ORDEN("Nº de orden"),
    CLIENTE("Cliente"),
    DIRECCION("Dirección"),
    BULTOS("Bultos"),
    KILOS("Kilos"),
    UBICACION("Ubicación"),
    ENCONTRADO("Encontrado"),
}

/** Minúsculas y sin acentos, para comparar y buscar. */
fun String.normalizado(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()

fun formatoKilos(k: Double): String =
    if (k == Math.floor(k)) k.toLong().toString() else String.format(java.util.Locale("es", "ES"), "%.2f", k)

private fun Mercancia.textoDe(campo: Campo): String = when (campo) {
    Campo.ORDEN -> orden.toString()
    Campo.NUM_ORDEN -> numOrden
    Campo.CLIENTE -> cliente
    Campo.DIRECCION -> "$direccion $poblacion"
    Campo.BULTOS -> bultosTexto
    Campo.KILOS -> formatoKilos(kilos)
    Campo.UBICACION -> ubicacion
    Campo.ENCONTRADO -> "$encontrados"
}

fun List<Mercancia>.ordenadas(campo: Campo, ascendente: Boolean): List<Mercancia> {
    val comparador: Comparator<Mercancia> = when (campo) {
        Campo.ORDEN -> compareBy { it.orden }
        Campo.NUM_ORDEN -> compareBy { it.numOrden }
        Campo.CLIENTE -> compareBy { it.cliente.normalizado() }
        Campo.DIRECCION -> compareBy { it.direccion.normalizado() }
        Campo.BULTOS -> compareBy<Mercancia> { it.bultosLlevar }.thenBy { it.bultosTotal }
        Campo.KILOS -> compareBy { it.kilos }
        // Las que no tienen ubicación van siempre al final.
        Campo.UBICACION -> return sortedWith(
            compareBy<Mercancia> { it.ubicacion.isBlank() }
                .then(
                    if (ascendente) compareBy { it.ubicacion.normalizado() }
                    else compareByDescending { it.ubicacion.normalizado() }
                )
                .thenBy { it.orden }
        )
        Campo.ENCONTRADO -> compareBy<Mercancia> { it.estado.ordinal }.thenBy { it.encontrados }
    }
    val conDesempate = comparador.thenBy { it.orden }.thenBy { it.id }
    return sortedWith(if (ascendente) conDesempate else comparador.reversed().thenBy { it.orden }.thenBy { it.id })
}

/** Filtra por texto en un campo concreto, o en todos si [campo] es null. */
fun List<Mercancia>.filtradas(texto: String, campo: Campo?): List<Mercancia> {
    val palabras = texto.normalizado().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (palabras.isEmpty()) return this
    return filter { m ->
        val donde = if (campo != null) {
            m.textoDe(campo).normalizado()
        } else {
            listOf(
                m.orden.toString(), m.numOrden, m.cliente, m.direccion, m.poblacion,
                m.bultosTexto, formatoKilos(m.kilos), m.ubicacion, m.observacion,
            ).joinToString(" ").normalizado()
        }
        palabras.all { donde.contains(it) }
    }
}

private val NUMEROS = mapOf(
    "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
    "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
)

private fun numero(s: String): Int? = s.toIntOrNull() ?: NUMEROS[s.normalizado()]

/**
 * Interpreta "2/1", "2 de 1", "2 barra 1" o "3" (= 3/3).
 * Devuelve (total, a llevar) o null si no se entiende.
 */
fun leerBultos(texto: String): Pair<Int, Int>? {
    val t = texto.normalizado().replace("barra", "/").replace(" de ", "/").replace(" ", "")
    val partes = t.split("/", "\\", "|").filter { it.isNotEmpty() }
    return when (partes.size) {
        1 -> numero(partes[0])?.takeIf { it > 0 }?.let { it to it }
        2 -> {
            val a = numero(partes[0]) ?: return null
            val b = numero(partes[1]) ?: return null
            if (a > 0 && b > 0) a to b else null
        }
        else -> null
    }
}

fun leerKilos(texto: String): Double? {
    val t = texto.normalizado().replace(" coma ", ",").replace(" con ", ",")
        .replace(Regex("kilos?|kg"), "").replace(" ", "")
    val limpio = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return limpio.toDoubleOrNull()?.takeIf { it >= 0 }
}
