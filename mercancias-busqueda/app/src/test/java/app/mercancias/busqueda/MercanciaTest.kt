package app.mercancias.busqueda

import app.mercancias.busqueda.model.Campo
import app.mercancias.busqueda.model.Estado
import app.mercancias.busqueda.model.Mercancia
import app.mercancias.busqueda.model.filtradas
import app.mercancias.busqueda.model.leerBultos
import app.mercancias.busqueda.model.leerKilos
import app.mercancias.busqueda.model.ordenadas
import app.mercancias.busqueda.ocr.Dictado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MercanciaTest {
    private val a = Mercancia(1, 8, "100", "Zapatería Ñu", "Barcelona", "Calle B 2", 1, 1, 168.0)
    private val b = Mercancia(2, 9, "200", "álvarez e hijos", "Badalona", "Avenida A 1", 4, 4, 35.5, encontrados = 2, ubicacion = "Pasillo 3")
    private val c = Mercancia(3, 10, "300", "Bar Centro", "Barcelona", "Ronda C 3", 2, 1, 1.0, encontrados = 1, ubicacion = "Muelle 1")

    @Test
    fun estadoSegunLosBultosEncontrados() {
        assertEquals(Estado.PENDIENTE, a.estado)
        // 4 bultos y 2 encontrados: sigue pendiente, con el aviso de lo que falta.
        assertEquals(Estado.PARCIAL, b.estado)
        assertEquals(2, b.faltan)
        assertEquals("Faltan 2 de 4 bultos (encontrados 2)", b.avisoFaltan)
        // 2/1: con 1 encontrado ya está completo, aunque en total haya 2.
        assertEquals(Estado.ENCONTRADO, c.estado)
        assertNull(c.avisoFaltan)
    }

    @Test
    fun bultos() {
        assertEquals(1 to 1, leerBultos("1/1"))
        assertEquals(2 to 1, leerBultos("2/1"))
        assertEquals(2 to 1, leerBultos("2 de 1"))
        assertEquals(2 to 1, leerBultos("dos barra uno"))
        assertEquals(6 to 6, leerBultos("6"))
        assertNull(leerBultos("abc"))
        assertNull(leerBultos("0/1"))
        assertNull(leerBultos(""))
    }

    @Test
    fun kilos() {
        assertEquals(168.0, leerKilos("168,00")!!, 0.001)
        assertEquals(35.5, leerKilos("35 coma 5 kilos")!!, 0.001)
        assertEquals(1234.5, leerKilos("1.234,5")!!, 0.001)
        assertEquals(12.5, leerKilos("12.5")!!, 0.001)
        assertNull(leerKilos("muchos"))
    }

    @Test
    fun ordenaPorCualquierCampo() {
        val l = listOf(c, a, b)
        assertEquals(listOf(1L, 2L, 3L), l.ordenadas(Campo.ORDEN, true).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), l.ordenadas(Campo.ORDEN, false).map { it.id })
        // Sin distinguir mayúsculas ni acentos.
        assertEquals(listOf(2L, 3L, 1L), l.ordenadas(Campo.CLIENTE, true).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), l.ordenadas(Campo.KILOS, true).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), l.ordenadas(Campo.DIRECCION, true).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), l.ordenadas(Campo.BULTOS, false).map { it.id }.let { listOf(it[0]) + it.drop(1).sorted().reversed() })
        // Las que no tienen ubicación, siempre al final.
        assertEquals(listOf(3L, 2L, 1L), l.ordenadas(Campo.UBICACION, true).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), l.ordenadas(Campo.UBICACION, false).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), l.ordenadas(Campo.ENCONTRADO, true).map { it.id })
    }

    @Test
    fun buscaEnTodoOEnUnCampo() {
        val l = listOf(a, b, c)
        assertEquals(listOf(2L), l.filtradas("ALVAREZ", null).map { it.id })
        assertEquals(listOf(1L, 3L), l.filtradas("barcelona", null).map { it.id })
        assertEquals(listOf(3L), l.filtradas("barcelona ronda", null).map { it.id })
        assertEquals(emptyList<Long>(), l.filtradas("barcelona", Campo.CLIENTE).map { it.id })
        assertEquals(listOf(2L), l.filtradas("pasillo", Campo.UBICACION).map { it.id })
        assertEquals(3, l.filtradas("  ", null).size)
    }

    @Test
    fun dictado() {
        val m = Dictado.interpretar("orden 12 cliente panadería López dirección calle Mayor 5 bultos 2 de 1 kilos 35 coma 5 ubicación pasillo 3")
        assertEquals(12, m.orden)
        assertEquals("Panadería López", m.cliente)
        assertEquals("Calle Mayor 5", m.direccion)
        assertEquals(2 to 1, m.bultosTotal to m.bultosLlevar)
        assertEquals(35.5, m.kilos, 0.001)
        assertEquals("Pasillo 3", m.ubicacion)

        // Sin palabras clave, todo es el cliente.
        assertEquals("Talleres Pérez", Dictado.interpretar("talleres Pérez").cliente)
        assertEquals("123456", Dictado.interpretar("número de orden 123 456 cliente Ana").numOrden)
    }
}
