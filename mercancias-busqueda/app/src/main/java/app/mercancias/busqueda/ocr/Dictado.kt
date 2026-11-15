package app.mercancias.busqueda.ocr

import app.mercancias.busqueda.model.Mercancia
import app.mercancias.busqueda.model.leerBultos
import app.mercancias.busqueda.model.leerKilos
import app.mercancias.busqueda.model.normalizado

/**
 * Interpreta una frase dictada del tipo:
 * «orden 12 cliente Panarra dirección Consell de Cent 185 bultos 2 de 1 kilos 35 ubicación pasillo 3».
 * Cada palabra clave abre un campo; lo que no lleve palabra clave delante se toma como cliente.
 */
object Dictado {
    private val CLAVES = listOf(
        "numero de orden" to "num", "numero orden" to "num", "orden" to "orden",
        "cliente" to "cliente", "direccion" to "direccion", "poblacion" to "poblacion",
        "bultos" to "bultos", "bulto" to "bultos", "kilos" to "kilos", "kilo" to "kilos",
        "ubicacion" to "ubicacion",
    )

    fun interpretar(frase: String): Mercancia {
        val palabras = frase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val norm = palabras.map { it.normalizado().trim(',', '.', ':') }
        val campos = linkedMapOf<String, MutableList<String>>()
        var actual = "cliente"
        var i = 0
        while (i < palabras.size) {
            val clave = CLAVES.firstOrNull { (k, _) ->
                val partes = k.split(" ")
                i + partes.size <= norm.size && partes.indices.all { norm[i + it] == partes[it] }
            }
            if (clave != null) {
                actual = clave.second
                campos.getOrPut(actual) { mutableListOf() }
                i += clave.first.split(" ").size
            } else {
                campos.getOrPut(actual) { mutableListOf() } += palabras[i]
                i++
            }
        }
        fun texto(c: String) = campos[c]?.joinToString(" ")?.trim(' ', ',', '.').orEmpty()

        val bultos = texto("bultos").takeIf { it.isNotEmpty() }?.let { leerBultos(it) }
        val kilos = texto("kilos").takeIf { it.isNotEmpty() }?.let { leerKilos(it) }
        val cliente = texto("cliente")
        return Mercancia(
            orden = texto("orden").filter { it.isDigit() }.toIntOrNull() ?: 0,
            numOrden = texto("num").filter { it.isDigit() },
            cliente = cliente.replaceFirstChar { it.uppercase() },
            poblacion = texto("poblacion").replaceFirstChar { it.uppercase() },
            direccion = texto("direccion").replaceFirstChar { it.uppercase() },
            bultosTotal = bultos?.first ?: 1,
            bultosLlevar = bultos?.second ?: 1,
            kilos = kilos ?: 0.0,
            ubicacion = texto("ubicacion").replaceFirstChar { it.uppercase() },
            revisar = cliente.isBlank() || (campos.containsKey("bultos") && bultos == null),
        )
    }
}
