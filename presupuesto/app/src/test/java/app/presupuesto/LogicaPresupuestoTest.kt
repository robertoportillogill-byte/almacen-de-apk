package app.presupuesto

import app.presupuesto.logica.AnalizadorTexto
import app.presupuesto.logica.Presupuesto
import app.presupuesto.logica.formatearImporte
import app.presupuesto.logica.parseImporte
import app.presupuesto.model.Borrador
import app.presupuesto.model.Movimiento
import app.presupuesto.model.Repeticion
import app.presupuesto.model.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LogicaPresupuestoTest {

    private val hoy = LocalDate.of(2026, 11, 25)

    @Test
    fun importes_en_formato_espanol_e_ingles() {
        assertEquals(123450L, parseImporte("1.234,50"))
        assertEquals(123450L, parseImporte("1,234.50"))
        assertEquals(1250L, parseImporte("12,50 €"))
        assertEquals(1250L, parseImporte("12.5"))
        assertEquals(150000L, parseImporte("1.500"))
        assertEquals(150000L, parseImporte("1500"))
        assertNull(parseImporte(""))
        assertNull(parseImporte("abc"))
        assertNull(parseImporte("0"))
    }

    @Test
    fun formatear_importe_ida_y_vuelta() {
        assertEquals("1.234,50", formatearImporte(123450))
        assertEquals("0,05", formatearImporte(5))
        assertEquals(123450L, parseImporte(formatearImporte(123450)))
    }

    @Test
    fun voz_gasto_en_cuotas() {
        val b = AnalizadorTexto.analizarVoz("gasto 600 euros televisor en 6 cuotas", hoy)
        assertEquals(Tipo.EGRESO, b.tipo)
        assertEquals("600,00", b.importe)
        assertEquals(Repeticion.CUOTAS, b.repeticion)
        assertEquals(6, b.veces)
        assertEquals("Televisor", b.concepto)
    }

    @Test
    fun voz_ingreso_mensual_con_meses() {
        val b = AnalizadorTexto.analizarVoz("ingreso 1.500 nómina mensual durante 3 meses", hoy)
        assertEquals(Tipo.INGRESO, b.tipo)
        assertEquals("1.500,00", b.importe)
        assertEquals(Repeticion.MENSUAL, b.repeticion)
        assertEquals(3, b.veces)
        assertEquals("Nómina", b.concepto)
    }

    @Test
    fun voz_fecha_explicita_y_ayer() {
        val b = AnalizadorTexto.analizarVoz("gasto 20 luz el 3 de marzo de 2026", hoy)
        assertEquals(LocalDate.of(2026, 3, 3), b.fecha)
        assertEquals("20,00", b.importe)
        assertEquals("Luz", b.concepto)

        val c = AnalizadorTexto.analizarVoz("gasto 5 euros café ayer", hoy)
        assertEquals(hoy.minusDays(1), c.fecha)
    }

    @Test
    fun documento_toma_el_total_y_la_fecha() {
        val texto = """
            SUPERMERCADO EJEMPLO
            Factura simplificada
            25/11/2026
            Pan 1,20
            Leche 0,95
            TOTAL 2,15
        """.trimIndent()
        val b = AnalizadorTexto.analizarDocumento(texto, hoy)
        assertEquals(Tipo.EGRESO, b.tipo)
        assertEquals(LocalDate.of(2026, 11, 25), b.fecha)
        assertEquals("2,15", b.importe)
        assertEquals("SUPERMERCADO EJEMPLO", b.concepto)
    }

    @Test
    fun cuotas_suman_exactamente_el_total() {
        // 100,00 € en 3 cuotas: 33,34 + 33,33 + 33,33
        val b = Borrador(tipo = Tipo.EGRESO, fecha = hoy, concepto = "Sofá",
            repeticion = Repeticion.CUOTAS, veces = 3)
        val filas = Presupuesto.generar(b, 10000L)
        assertEquals(3, filas.size)
        assertEquals(10000L, filas.sumOf { it.importeCent })
        assertEquals(listOf(3334L, 3333L, 3333L), filas.map { it.importeCent })
        assertEquals(listOf(1, 2, 3), filas.map { it.indice })
        assertEquals(LocalDate.of(2027, 1, 25), LocalDate.parse(filas[2].fecha))
        assertEquals(1, filas.map { it.grupo }.toSet().size)
    }

    @Test
    fun recurrente_mensual_genera_un_mes_cada_vez() {
        val b = Borrador(tipo = Tipo.INGRESO, fecha = hoy, concepto = "Alquiler",
            repeticion = Repeticion.MENSUAL, veces = 12)
        val filas = Presupuesto.generar(b, 80000L)
        assertEquals(12, filas.size)
        assertEquals(LocalDate.of(2026, 12, 25), LocalDate.parse(filas[1].fecha))
        assertEquals(960000L, filas.sumOf { it.importeFirmado })
    }

    @Test
    fun saldo_corrido_en_orden_cronologico() {
        val movs = listOf(
            Movimiento("b", "2026-11-10", "Gasto", 5000, Tipo.EGRESO),
            Movimiento("a", "2026-11-01", "Nómina", 200000, Tipo.INGRESO),
            Movimiento("c", "2026-12-01", "Alquiler", 80000, Tipo.EGRESO)
        )
        val filas = Presupuesto.conSaldo(movs)
        assertEquals(listOf(200000L, 195000L, 115000L), filas.map { it.saldo })

        val r = Presupuesto.resumen(movs, hoy)
        assertEquals(195000L, r.saldoHoy)        // solo lo que ya ha ocurrido
        assertEquals(115000L, r.saldoPrevisto)   // incluye el alquiler de diciembre
        assertEquals(200000L, r.ingresosMes)
        assertEquals(5000L, r.egresosMes)
    }
}
