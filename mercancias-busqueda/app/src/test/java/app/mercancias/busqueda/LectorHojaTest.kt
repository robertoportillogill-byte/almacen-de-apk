package app.mercancias.busqueda

import app.mercancias.busqueda.ocr.LectorHoja
import app.mercancias.busqueda.ocr.Palabra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hoja de ruta inventada, con la misma disposición que la real: columnas, líneas secundarias, papel torcido y boli. */
class LectorHojaTest {
    private val alto = 30f
    private val palabras = mutableListOf<Palabra>()

    private fun escribir(texto: String, x: Float, y: Float, h: Float = alto) {
        var cx = x
        for (p in texto.split(" ")) {
            val ancho = p.length * 19f
            palabras += Palabra(p, cx, y, ancho, h)
            cx += ancho + 12f
        }
    }

    private fun fila(i: Int, orden: Int, num: String, cliente: String, cliente2: String?, direccion: String,
                     direccion2: String?, bultos: String?, kilos: String) {
        val y = 565f + 270f * i
        escribir(orden.toString(), 100f, y)
        escribir(num, 200f, y)
        escribir("E", 540f, y)
        escribir(cliente, 660f, y)
        escribir("BARCELONA", 1210f, y)
        escribir(direccion, 1585f, y)
        bultos?.let { escribir(it, 2335f, y) }
        escribir("0,00", 2470f, y)
        escribir(kilos, 2600f, y)
        escribir("708_6", 2755f, y)
        escribir("PALET", 2980f, y)
        escribir("Ref.: $num", 245f, y + 43f)
        cliente2?.let { escribir(it, 660f, y + 43f) }
        direccion2?.let { escribir(it, 1585f, y + 43f) }
        escribir("30/10/202", 30f, y + 90f)
        escribir("Cliente: N/A", 245f, y + 90f)
        escribir("Remitente: 00000000-01 REMITENTE FALSO SL", 245f, y + 135f)
        escribir("Instrucciones especiales: N", 245f, y + 180f)
    }

    private fun hoja(inclinacion: Float): List<Palabra> {
        palabras.clear()
        escribir("Delegación: 000 - PRUEBA", 767f, 209f)
        escribir("Zona: 708_6", 767f, 384f)
        escribir("Orden", 15f, 485f); escribir("Nº de Orden", 245f, 485f); escribir("Tipo", 535f, 485f)
        escribir("Cliente", 658f, 485f); escribir("Población", 1207f, 485f); escribir("Dirección", 1582f, 485f)
        escribir("Bulto", 2330f, 485f); escribir("Palet", 2461f, 485f); escribir("Kilos", 2614f, 485f)
        escribir("Zona", 2775f, 485f); escribir("Producto", 2949f, 485f)
        fila(0, 8, "999900000001", "FERRETERIA INVENTADA LARGA Y", "S.L.", "CALLE FALSA 123, 08001", null, "1/1", "168,00")
        fila(1, 9, "999900000002", "TALLERES EJEMPLO", null, "AVENIDA DE PRUEBA 3, PLANTA 1, PUERTA", "4, 08004", "2/1", "1,00")
        fila(2, 10, "999900000003", "MARIA EJEMPLO", null, "PASEO IMAGINARIO 52 LOCAL 1, 08014", null, null, "346")
        fila(3, 11, "999900000004", "BAR DE MENTIRA", null, "RONDA SIMULADA 171, 08015", null, "6/6", "57,00")
        // Anotación a boli encima de una dirección y código de barras leído como número suelto.
        escribir("DESC M10", 1650f, 565f + 270f * 2 + 20f, h = 95f)
        escribir("99990000099", 1941f, 139f)
        // El papel escaneado nunca está recto.
        return palabras.map { it.copy(x = it.x + inclinacion * it.y, y = it.y - inclinacion * it.x) }
    }

    @Test
    fun leeTodasLasFilasConElPapelTorcido() {
        for (inclinacion in listOf(0f, 0.018f, -0.03f)) {
            val filas = LectorHoja.leer(hoja(inclinacion))
            assertEquals("inclinación $inclinacion", 4, filas.size)

            val f0 = filas[0]
            assertEquals(8, f0.orden)
            assertEquals("999900000001", f0.numOrden)
            assertEquals("FERRETERIA INVENTADA LARGA Y S.L.", f0.cliente)
            assertEquals("BARCELONA", f0.poblacion)
            assertEquals("CALLE FALSA 123, 08001", f0.direccion)
            assertEquals(1 to 1, f0.bultosTotal to f0.bultosLlevar)
            assertEquals(168.0, f0.kilos, 0.001)
            assertFalse(f0.dudosa)

            // 2/1 = hay 2, llevar 1; y la dirección ocupa dos líneas.
            val f1 = filas[1]
            assertEquals(2 to 1, f1.bultosTotal to f1.bultosLlevar)
            assertEquals("AVENIDA DE PRUEBA 3, PLANTA 1, PUERTA 4, 08004", f1.direccion)
            assertEquals(1.0, f1.kilos, 0.001)

            // Bultos sin leer: se marca como dudosa. La anotación a boli no se cuela en la dirección.
            val f2 = filas[2]
            assertTrue(f2.dudosa)
            assertEquals("PASEO IMAGINARIO 52 LOCAL 1, 08014", f2.direccion)
            assertEquals(346.0, f2.kilos, 0.001)

            assertEquals(6 to 6, filas[3].bultosTotal to filas[3].bultosLlevar)
            assertEquals(57.0, filas[3].kilos, 0.001)
        }
    }

    @Test
    fun sinCabecerasSigueSeparandoLasColumnas() {
        val sinCabecera = hoja(0f).filterNot { it.y in 470f..500f }
        val filas = LectorHoja.leer(sinCabecera)
        assertEquals(4, filas.size)
        assertEquals("TALLERES EJEMPLO", filas[1].cliente)
        assertEquals("BARCELONA", filas[1].poblacion)
        assertEquals(2 to 1, filas[1].bultosTotal to filas[1].bultosLlevar)
    }

    @Test
    fun textoQueNoEsUnaHojaNoDaFilas() {
        palabras.clear()
        escribir("Esto es una foto cualquiera con el teléfono 934000000", 100f, 100f)
        assertTrue(LectorHoja.leer(palabras.toList()).isEmpty())
        assertTrue(LectorHoja.leer(emptyList()).isEmpty())
    }
}
