package app.mercancias.busqueda.ocr

import app.mercancias.busqueda.model.normalizado
import kotlin.math.abs

/** Palabra reconocida por el OCR, con su caja en píxeles (x, y = esquina superior izquierda). */
data class Palabra(val texto: String, val x: Float, val y: Float, val ancho: Float, val alto: Float) {
    val cx get() = x + ancho / 2
    val cy get() = y + alto / 2
    val derecha get() = x + ancho
}

data class FilaLeida(
    val orden: Int,
    val numOrden: String,
    val cliente: String,
    val poblacion: String,
    val direccion: String,
    val bultosTotal: Int,
    val bultosLlevar: Int,
    val kilos: Double,
    /** Falta algún dato o se ha leído con dudas. */
    val dudosa: Boolean,
)

/**
 * Convierte las palabras del OCR de una hoja de ruta en filas.
 *
 * La hoja es una tabla: cada envío empieza por su «Nº de Orden» (un número largo) y en esa
 * misma línea van cliente, población, dirección, bultos, palet y kilos. Debajo vienen
 * Ref., Cliente:, Remitente e Instrucciones, que no se usan.
 *
 * Se trabaja por posición: las cabeceras marcan dónde empieza cada columna y el número de
 * orden marca dónde empieza cada fila. Las anotaciones a boli se descartan por tamaño
 * (son bastante más altas que la letra impresa).
 */
object LectorHoja {

    private val NUM_ORDEN = Regex("^\\d{10,13}$")
    private val BULTOS = Regex("^\\(?(\\d{1,3})[/\\\\|Il!](\\d{1,3})\\)?$")
    private val DECIMAL = Regex("^\\d{1,5}[.,]\\d{1,2}$")
    private val NUMERO = Regex("^\\d{1,5}([.,]\\d{1,2})?$")

    fun leer(entrada: List<Palabra>): List<FilaLeida> {
        val todas = entrada.map { it.copy(texto = it.texto.trim()) }
            .filter { it.texto.isNotEmpty() && it.alto > 0 && it.ancho > 0 }
        if (todas.isEmpty()) return emptyList()

        val h = mediana(
            todas.filter { p -> p.texto.count { it.isLetterOrDigit() } >= 3 }.map { it.alto }
                .ifEmpty { todas.map { it.alto } }
        )

        // 1) Inclinación del papel, a partir de los números de orden (deben estar en vertical).
        val candidatas = todas.filter { esNumOrden(it, todas, h) }
        val b = inclinacion(candidatas, h)
        val palabras = todas.map { it.copy(x = it.x - b * it.y, y = it.y + b * it.x) }

        // 2) Filas: números de orden alineados en la misma columna.
        var anclas = palabras.filter { esNumOrden(it, palabras, h) }
        if (anclas.isEmpty()) return emptyList()
        val xAncla = mediana(anclas.map { it.x })
        anclas = anclas.filter { abs(it.x - xAncla) < 2.5f * h }.sortedBy { it.cy }

        // 3) Columnas, por las cabeceras; si no se leen, por proporciones habituales.
        val primeraFila = anclas.first().cy
        val cab = cabeceras(palabras, primeraFila, h)
        val impresas = palabras.filter { it.alto <= 1.9f * h && it.texto.any { c -> c.isLetterOrDigit() } }
        val xBultosVisto = impresas.filter { BULTOS.matches(it.texto) && it.x > xAncla + 10 * h }
            .map { it.x }.let { if (it.isEmpty()) null else mediana(it) }

        val cCli: Float
        val cPob: Float
        val cDir: Float
        val hayCabecera = cab["cliente"] != null && cab["poblacion"] != null && cab["direccion"] != null
        if (hayCabecera) {
            cCli = cab.getValue("cliente"); cPob = cab.getValue("poblacion"); cDir = cab.getValue("direccion")
        } else {
            val fin = cab["bulto"] ?: xBultosVisto ?: cab["palet"]?.let { it - 4.5f * h }
                ?: (impresas.maxOf { it.derecha } * 0.70f)
            val tramo = fin - xAncla
            cCli = xAncla + 0.20f * tramo; cPob = xAncla + 0.465f * tramo; cDir = xAncla + 0.64f * tramo
        }
        val cPal = cab["palet"]
        val cKil = cab["kilos"]
        val cZon = cab["zona"]
        val cBul = cab["bulto"] ?: xBultosVisto
        val finDireccion = when {
            cBul != null -> cBul - 0.5f * h
            cPal != null -> cPal - 5f * h
            else -> Float.MAX_VALUE
        }
        val tol = 0.7f * h

        return anclas.mapIndexed { i, ancla ->
            val y0 = ancla.cy
            val siguiente = anclas.getOrNull(i + 1)?.cy
            // La fila útil acaba donde empieza la línea «Cliente: N/A».
            val lineaCliente = impresas.filter {
                it.texto.normalizado().startsWith("cliente") && it.x < cCli - h &&
                    it.cy > y0 + h && it.cy < y0 + 5 * h
            }.minByOrNull { it.cy }
            var yFin = lineaCliente?.let { it.y - 0.2f * h } ?: (y0 + 2.2f * h)
            if (siguiente != null) yFin = minOf(yFin, siguiente - 0.8f * h)

            val banda = impresas.filter { it.cy >= y0 - 0.8f * h && it.cy < yFin && it !== ancla }
            val linea1 = banda.filter { abs(it.cy - y0) < 0.75f * h }

            val orden = linea1.filter { it.derecha < ancla.x + tol && it.texto.matches(Regex("^\\d{1,3}$")) }
                .maxByOrNull { it.x }?.texto?.toIntOrNull() ?: 0

            // Bultos
            val zonaBultos = linea1.filter { it.x >= cDir && (cPal == null || it.x < cPal - 0.3f * h) }
            val bultos = zonaBultos.filter { BULTOS.matches(it.texto) }
                .minByOrNull { if (cBul != null) abs(it.x - cBul) else -it.x }
            val (total, llevar) = bultos?.let {
                val m = BULTOS.find(it.texto)!!
                m.groupValues[1].toInt() to m.groupValues[2].toInt()
            } ?: (0 to 0)

            // Kilos
            val kilosPalabra = if (cKil != null) {
                val hasta = cZon?.let { it - 1.5f * h } ?: (cKil + 3f * h)
                linea1.filter { it.x >= cKil - 2f * h && it.x < hasta && NUMERO.matches(arreglarCifras(it.texto)) }
                    .minByOrNull { abs(it.cx - (cKil + 1.2f * h)) }
            } else {
                val decimales = linea1.filter { it.x > cDir && DECIMAL.matches(arreglarCifras(it.texto)) }.sortedBy { it.x }
                decimales.getOrNull(1) ?: decimales.firstOrNull()
            }
            val kilos = kilosPalabra?.let { arreglarCifras(it.texto).replace(',', '.').toDoubleOrNull() }

            val cliente = unir(banda.filter { it.x >= cCli - tol && it.x < cPob - tol }, h)
            val poblacion = unir(banda.filter { it.x >= cPob - tol && it.x < cDir - tol }, h)
            val direccion = unir(
                banda.filter { it.x >= cDir - tol && it.x < finDireccion && it !== bultos && it !== kilosPalabra }, h
            )

            FilaLeida(
                orden = orden,
                numOrden = ancla.texto,
                cliente = cliente,
                poblacion = poblacion,
                direccion = direccion,
                bultosTotal = if (total > 0) total else 1,
                bultosLlevar = if (llevar > 0) llevar else 1,
                kilos = kilos ?: 0.0,
                dudosa = bultos == null || total <= 0 || llevar <= 0 || kilos == null || cliente.isBlank(),
            )
        }
    }

    /** Cuántas filas se reconocen: sirve para elegir el giro correcto de la foto. */
    fun contarFilas(palabras: List<Palabra>): Int = leer(palabras).size

    private fun esNumOrden(p: Palabra, todas: List<Palabra>, h: Float): Boolean {
        if (!NUM_ORDEN.matches(p.texto) || p.alto > 1.9f * h) return false
        // El mismo número se repite detrás de «Ref.:» en la línea de abajo: ese no cuenta.
        return todas.none {
            it.texto.normalizado().startsWith("ref") && abs(it.cy - p.cy) < 0.7f * h &&
                it.x < p.x && p.x - it.derecha < 3 * h
        }
    }

    /** Pendiente x/y de la columna de números de orden (0 si el papel está recto). */
    private fun inclinacion(anclas: List<Palabra>, h: Float): Float {
        var pts = anclas
        if (pts.size < 3) return 0f
        val xm = mediana(pts.map { it.x })
        pts = pts.filter { abs(it.x - xm) < 6 * h }
        repeat(2) {
            if (pts.size < 3) return 0f
            val (a, b) = recta(pts)
            val buenos = pts.filter { abs(it.x - (a + b * it.y)) < 1.5f * h }
            if (buenos.size == pts.size) return b.coerceIn(-0.12f, 0.12f)
            pts = buenos
        }
        return if (pts.size >= 3) recta(pts).second.coerceIn(-0.12f, 0.12f) else 0f
    }

    private fun recta(pts: List<Palabra>): Pair<Float, Float> {
        val my = pts.map { it.y }.average().toFloat()
        val mx = pts.map { it.x }.average().toFloat()
        val den = pts.sumOf { ((it.y - my) * (it.y - my)).toDouble() }.toFloat()
        if (den < 1f) return mx to 0f
        val b = pts.sumOf { ((it.y - my) * (it.x - mx)).toDouble() }.toFloat() / den
        return (mx - b * my) to b
    }

    /** Posición x de cada cabecera de columna (las que se hayan leído). */
    private fun cabeceras(palabras: List<Palabra>, primeraFila: Float, h: Float): Map<String, Float> {
        val arriba = palabras.filter { it.cy < primeraFila - 0.5f * h && it.cy > primeraFila - 8 * h }
        fun buscar(nombre: String) = arriba.filter {
            !it.texto.contains(':') && it.texto.normalizado().trim('.', ',', ';') == nombre
        }
        val guia = (buscar("direccion") + buscar("poblacion")).maxByOrNull { it.cy } ?: return emptyMap()
        val res = mutableMapOf<String, Float>()
        for (n in listOf("cliente", "poblacion", "direccion", "bulto", "palet", "kilos", "zona")) {
            buscar(n).filter { abs(it.cy - guia.cy) < 1.5f * h }.minByOrNull { abs(it.cy - guia.cy) }
                ?.let { res[n] = it.x }
        }
        return res
    }

    private fun arreglarCifras(t: String): String =
        t.replace('O', '0').replace('o', '0').trim('.', ',', ';', ':', '•', '*')

    /** Une las palabras en orden de lectura (por líneas, de izquierda a derecha). */
    private fun unir(ps: List<Palabra>, h: Float): String {
        if (ps.isEmpty()) return ""
        val lineas = mutableListOf<MutableList<Palabra>>()
        for (p in ps.sortedBy { it.cy }) {
            val ultima = lineas.lastOrNull()
            if (ultima != null && abs(ultima.map { it.cy }.average().toFloat() - p.cy) < 0.6f * h) ultima += p
            else lineas += mutableListOf(p)
        }
        return lineas.joinToString(" ") { l -> l.sortedBy { it.x }.joinToString(" ") { it.texto } }
            .replace(Regex("\\s+([,.;:])"), "$1")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '-', ';', ':', '.')
            .let { if (it.endsWith("S.L") || it.endsWith("S.A")) "$it." else it }
    }

    private fun mediana(v: List<Float>): Float {
        if (v.isEmpty()) return 0f
        val o = v.sorted()
        return if (o.size % 2 == 1) o[o.size / 2] else (o[o.size / 2 - 1] + o[o.size / 2]) / 2
    }
}
