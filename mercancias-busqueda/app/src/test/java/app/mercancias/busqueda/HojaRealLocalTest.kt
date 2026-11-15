package app.mercancias.busqueda

import app.mercancias.busqueda.ocr.LectorHoja
import app.mercancias.busqueda.ocr.Palabra
import org.junit.Test
import java.io.File

/** Prueba local con un TSV de tesseract de una hoja real (no se sube: contiene datos de clientes). */
class HojaRealLocalTest {
    @Test
    fun hojaReal() {
        val ruta = System.getenv("HOJA_TSV") ?: return
        val palabras = File(ruta).readLines().drop(1).mapNotNull { l ->
            val c = l.split("\t")
            if (c.size < 12 || c[11].isBlank() || (c[10].toFloatOrNull() ?: -1f) < 0f) null
            else Palabra(c[11], c[6].toFloat(), c[7].toFloat(), c[8].toFloat(), c[9].toFloat())
        }
        LectorHoja.leer(palabras).forEach { println("FILA> $it") }
    }
}
