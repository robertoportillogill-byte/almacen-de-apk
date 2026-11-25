package app.presupuesto

import app.presupuesto.logica.Almacen
import app.presupuesto.logica.AnalizadorTexto
import app.presupuesto.logica.Presupuesto
import app.presupuesto.logica.formatearImporte
import app.presupuesto.logica.formatearSaldo
import app.presupuesto.logica.parseSaldo
import app.presupuesto.logica.parseImporte
import app.presupuesto.model.Borrador
import app.presupuesto.model.Cuenta
import app.presupuesto.model.Datos
import app.presupuesto.model.Movimiento
import app.presupuesto.model.Repeticion
import app.presupuesto.model.Tipo
import app.presupuesto.model.TipoCuenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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

        val r = Presupuesto.resumen(emptyList(), movs, hoy)
        assertEquals(195000L, r.saldoHoy)        // solo lo que ya ha ocurrido
        assertEquals(115000L, r.saldoPrevisto)   // incluye el alquiler de diciembre
        assertEquals(200000L, r.ingresosMes)
        assertEquals(5000L, r.egresosMes)
    }

    // ---------- Cuentas ----------

    private val caja = Cuenta("caja", "Efectivo", TipoCuenta.CAJA, 10000)       // 100 €
    private val banco = Cuenta("banco", "Santander", TipoCuenta.BANCO, 100000)  // 1.000 €
    private val visa = Cuenta("visa", "Visa", TipoCuenta.TARJETA, -30000)       // debe 300 €
    private val cuentas = listOf(caja, banco, visa)

    @Test
    fun saldos_por_cuenta_y_total_general() {
        val movs = listOf(
            Movimiento("1", "2026-11-02", "Nómina", 150000, Tipo.INGRESO, cuentaId = "banco"),
            Movimiento("2", "2026-11-03", "Café", 250, Tipo.EGRESO, cuentaId = "caja"),
            Movimiento("3", "2026-11-04", "Gasolina", 6000, Tipo.EGRESO, cuentaId = "visa"),
            Movimiento("4", "2026-12-01", "Alquiler", 70000, Tipo.EGRESO, cuentaId = "banco")
        )
        val saldos = Presupuesto.saldosCuentas(cuentas, movs, hoy).associate { it.cuenta.id to it.saldo }
        assertEquals(9750L, saldos["caja"])
        assertEquals(250000L, saldos["banco"])   // el alquiler de diciembre aún no cuenta
        assertEquals(-36000L, saldos["visa"])

        val porTipo = Presupuesto.totalesPorTipo(Presupuesto.saldosCuentas(cuentas, movs, hoy))
        assertEquals(9750L, porTipo[TipoCuenta.CAJA])
        assertEquals(-36000L, porTipo[TipoCuenta.TARJETA])

        val r = Presupuesto.resumen(cuentas, movs, hoy)
        assertEquals(9750L + 250000L - 36000L, r.saldoHoy)
        assertEquals(r.saldoHoy - 70000L, r.saldoPrevisto)
    }

    @Test
    fun datos_antiguos_pasan_a_una_caja_efectivo() {
        val antiguos = Datos(movimientos = listOf(Movimiento("1", "2026-11-01", "Pan", 120, Tipo.EGRESO)))
        val d = Presupuesto.asegurarCuentas(antiguos) { "nueva" }
        assertEquals(1, d.cuentas.size)
        assertEquals("Efectivo", d.cuentas[0].nombre)
        assertEquals(TipoCuenta.CAJA, d.cuentas[0].tipo)
        assertEquals("nueva", d.movimientos[0].cuentaId)
        // Si ya está bien, no cambia nada.
        assertEquals(d, Presupuesto.asegurarCuentas(d) { "otra" })
    }

    @Test
    fun almacen_lee_el_formato_de_la_version_1() {
        val f = File.createTempFile("presupuesto", ".json")
        f.writeText("""[{"id":"1","fecha":"2026-11-01","concepto":"Pan","importeCent":120,"tipo":"EGRESO"}]""")
        val d = Almacen(f).cargar()
        assertEquals(1, d.movimientos.size)
        assertEquals("Pan", d.movimientos[0].concepto)

        // Y lo que se guarda se vuelve a leer igual.
        val conCuentas = Presupuesto.asegurarCuentas(d) { "c" }
        Almacen(f).guardar(conCuentas)
        assertEquals(conCuentas, Almacen(f).cargar())
        f.delete()
    }

    @Test
    fun saldo_inicial_admite_cero_y_negativos() {
        assertEquals(0L, parseSaldo(""))
        assertEquals(0L, parseSaldo("0"))
        assertEquals(0L, parseSaldo("0,00"))
        assertEquals(-35000L, parseSaldo("-350,00"))
        assertEquals(125050L, parseSaldo("1.250,50"))
        assertNull(parseSaldo("abc"))
        assertEquals("-350,00", formatearSaldo(-35000))
    }

    @Test
    fun voz_detecta_la_cuenta() {
        assertEquals(TipoCuenta.TARJETA, AnalizadorTexto.analizarVoz("gasto 40 euros gasolina con tarjeta", hoy).tipoCuenta)
        val efectivo = AnalizadorTexto.analizarVoz("gasto 3 euros pan en efectivo", hoy)
        assertEquals(TipoCuenta.CAJA, efectivo.tipoCuenta)
        assertEquals("Pan", efectivo.concepto)
        assertNull(AnalizadorTexto.analizarVoz("gasto 3 euros pan", hoy).tipoCuenta)
    }

    // ---------- Previsión ----------

    @Test
    fun prevision_encuentra_el_punto_mas_bajo() {
        // Hoy hay 800 €. El 1/12 sale el alquiler (-1.000) y el 5/12 entra la nómina (+1.500).
        val c = listOf(Cuenta("b", "Banco", TipoCuenta.BANCO, 80000))
        val movs = listOf(
            Movimiento("alq", "2026-12-01", "Alquiler", 100000, Tipo.EGRESO, cuentaId = "b"),
            Movimiento("nom", "2026-12-05", "Nómina", 150000, Tipo.INGRESO, cuentaId = "b")
        )
        val p = Presupuesto.prevision(c, movs, hoy, LocalDate.of(2026, 12, 31))
        assertEquals(130000L, p.saldo)                       // al final se acaba en positivo
        assertEquals(-20000L, p.minimo)                      // pero el 1/12 se queda en -200 €
        assertEquals(LocalDate.of(2026, 12, 1), p.fechaMinimo)
        assertEquals(listOf("alq"), p.gastosAplazables.map { it.id })
    }

    @Test
    fun sugerir_mueve_los_menos_gastos_posibles() {
        // 100 € hoy; tres gastos futuros de 30, 90 y 20 € antes del día 15. Faltan 40 €.
        val c = listOf(Cuenta("b", "Banco", TipoCuenta.BANCO, 10000))
        val movs = listOf(
            Movimiento("g30", "2026-12-02", "Luz", 3000, Tipo.EGRESO, cuentaId = "b"),
            Movimiento("g90", "2026-12-05", "Seguro", 9000, Tipo.EGRESO, cuentaId = "b"),
            Movimiento("g20", "2026-12-10", "Teléfono", 2000, Tipo.EGRESO, cuentaId = "b")
        )
        val fecha = LocalDate.of(2026, 12, 15)
        val destino = LocalDate.of(2027, 1, 1)
        assertTrue(Presupuesto.prevision(c, movs, hoy, fecha).minimo < 0)

        val sel = Presupuesto.sugerirAplazar(c, movs, hoy, fecha, destino)
        assertEquals(setOf("g90"), sel)                      // basta con aplazar el seguro
        val despues = Presupuesto.prevision(c, Presupuesto.aplazar(movs, sel, destino), hoy, fecha)
        assertTrue(despues.minimo >= 0)

        // Mover a una fecha que no es posterior no arregla nada: no se sugiere.
        assertEquals(emptySet<String>(), Presupuesto.sugerirAplazar(c, movs, hoy, fecha, fecha))
    }

    @Test
    fun generar_guarda_la_cuenta_elegida() {
        val b = Borrador(tipo = Tipo.EGRESO, fecha = hoy, concepto = "Tele", repeticion = Repeticion.CUOTAS,
            veces = 2, cuentaId = "visa")
        assertTrue(Presupuesto.generar(b, 1000).all { it.cuentaId == "visa" })
    }
}
