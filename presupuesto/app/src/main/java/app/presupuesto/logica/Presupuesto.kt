package app.presupuesto.logica

import app.presupuesto.model.Borrador
import app.presupuesto.model.Cuenta
import app.presupuesto.model.Datos
import app.presupuesto.model.Movimiento
import app.presupuesto.model.Repeticion
import app.presupuesto.model.Tipo
import app.presupuesto.model.TipoCuenta
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Fila lista para pintar: el movimiento y el saldo total acumulado después de él. */
data class Fila(val mov: Movimiento, val saldo: Long)

data class Resumen(
    val saldoHoy: Long,       // saldos iniciales + movimientos con fecha <= hoy
    val saldoPrevisto: Long,  // todos, incluidas las cuotas y meses futuros
    val ingresosMes: Long,
    val egresosMes: Long
)

data class SaldoCuenta(val cuenta: Cuenta, val saldo: Long)

/** Con cuánto dinero se cuenta en [fecha] y cuál es el peor momento hasta llegar a ella. */
data class Prevision(
    val fecha: LocalDate,
    val saldo: Long,
    val porCuenta: List<SaldoCuenta>,
    val minimo: Long,                       // saldo más bajo entre hoy y la fecha (incluida)
    val fechaMinimo: LocalDate,
    val gastosAplazables: List<Movimiento>  // gastos futuros hasta la fecha, en orden cronológico
)

object Presupuesto {

    /**
     * Convierte un borrador en las filas que hay que guardar.
     * - Único: una fila.
     * - Mensual: una fila por mes, desde la fecha indicada.
     * - Cuotas: el importe total se reparte en N cuotas; si no divide exacto, las primeras cuotas
     *   suman 1 céntimo más para que la suma sea siempre el total.
     */
    fun generar(b: Borrador, importeCent: Long): List<Movimiento> {
        fun fila(fecha: LocalDate, cent: Long, rep: Repeticion, grupo: String?, indice: Int, total: Int) =
            Movimiento(
                id = UUID.randomUUID().toString(),
                fecha = fecha.toString(),
                concepto = b.concepto,
                importeCent = cent,
                tipo = b.tipo,
                repeticion = rep,
                grupo = grupo,
                indice = indice,
                total = total,
                cuentaId = b.cuentaId
            )

        return when (b.repeticion) {
            Repeticion.NINGUNA -> listOf(fila(b.fecha, importeCent, Repeticion.NINGUNA, null, 1, 1))

            Repeticion.MENSUAL -> {
                val grupo = UUID.randomUUID().toString()
                (0 until b.veces).map { i ->
                    fila(b.fecha.plusMonths(i.toLong()), importeCent, Repeticion.MENSUAL, grupo, i + 1, b.veces)
                }
            }

            Repeticion.CUOTAS -> {
                val grupo = UUID.randomUUID().toString()
                val n = b.veces
                val base = importeCent / n
                val resto = (importeCent % n).toInt()
                (0 until n).map { i ->
                    fila(
                        b.fecha.plusMonths(i.toLong()),
                        base + if (i < resto) 1 else 0,
                        Repeticion.CUOTAS, grupo, i + 1, n
                    )
                }
            }
        }
    }

    /**
     * Garantiza que hay al menos una cuenta y que todo movimiento apunta a una cuenta que existe.
     * Los datos de la versión 1.0 (sin cuentas) pasan a una caja llamada «Efectivo».
     */
    fun asegurarCuentas(d: Datos, nuevoId: () -> String = { UUID.randomUUID().toString() }): Datos {
        val cuentas = d.cuentas.ifEmpty { listOf(Cuenta(nuevoId(), "Efectivo", TipoCuenta.CAJA, 0)) }
        val ids = cuentas.map { it.id }.toSet()
        val porDefecto = cuentas.first().id
        val movs = d.movimientos.map { m ->
            if (m.cuentaId == null || m.cuentaId !in ids) m.copy(cuentaId = porDefecto) else m
        }
        return Datos(cuentas, movs)
    }

    fun saldoInicial(cuentas: List<Cuenta>): Long = cuentas.sumOf { it.saldoInicialCent }

    /** Ordena cronológicamente (a igualdad de fecha, respeta el orden de alta) y calcula el saldo corrido. */
    fun conSaldo(movs: List<Movimiento>, inicial: Long = 0): List<Fila> {
        var acumulado = inicial
        return movs.sortedBy { it.fecha }.map { m ->
            acumulado += m.importeFirmado
            Fila(m, acumulado)
        }
    }

    fun resumen(cuentas: List<Cuenta>, movs: List<Movimiento>, hoy: LocalDate): Resumen {
        val inicial = saldoInicial(cuentas)
        val mes = YearMonth.from(hoy)
        val delMes = movs.filter { YearMonth.from(it.fechaLocal) == mes }
        return Resumen(
            saldoHoy = inicial + movs.filter { !it.fechaLocal.isAfter(hoy) }.sumOf { it.importeFirmado },
            saldoPrevisto = inicial + movs.sumOf { it.importeFirmado },
            ingresosMes = delMes.filter { it.tipo == Tipo.INGRESO }.sumOf { it.importeCent },
            egresosMes = delMes.filter { it.tipo == Tipo.EGRESO }.sumOf { it.importeCent }
        )
    }

    /** Saldo de cada cuenta en una fecha (incluidos los movimientos de ese día). */
    fun saldosCuentas(cuentas: List<Cuenta>, movs: List<Movimiento>, hasta: LocalDate): List<SaldoCuenta> =
        cuentas.map { c ->
            SaldoCuenta(
                c,
                c.saldoInicialCent + movs.filter { it.cuentaId == c.id && !it.fechaLocal.isAfter(hasta) }
                    .sumOf { it.importeFirmado }
            )
        }

    fun totalesPorTipo(saldos: List<SaldoCuenta>): Map<TipoCuenta, Long> =
        TipoCuenta.entries.associateWith { t -> saldos.filter { it.cuenta.tipo == t }.sumOf { it.saldo } }

    fun prevision(cuentas: List<Cuenta>, movs: List<Movimiento>, hoy: LocalDate, fecha: LocalDate): Prevision {
        val inicial = saldoInicial(cuentas)
        val saldo = inicial + movs.filter { !it.fechaLocal.isAfter(fecha) }.sumOf { it.importeFirmado }

        // Recorrido día a día desde hoy hasta la fecha, para encontrar el punto más bajo.
        val corte = if (fecha.isBefore(hoy)) fecha else hoy
        var acumulado = inicial + movs.filter { !it.fechaLocal.isAfter(corte) }.sumOf { it.importeFirmado }
        var minimo = acumulado
        var fechaMinimo = corte
        movs.filter { it.fechaLocal.isAfter(corte) && !it.fechaLocal.isAfter(fecha) }
            .groupBy { it.fechaLocal }
            .toSortedMap()
            .forEach { (dia, delDia) ->
                acumulado += delDia.sumOf { it.importeFirmado }
                if (acumulado < minimo) {
                    minimo = acumulado
                    fechaMinimo = dia
                }
            }

        val aplazables = movs
            .filter { it.tipo == Tipo.EGRESO && it.fechaLocal.isAfter(hoy) && !it.fechaLocal.isAfter(fecha) }
            .sortedBy { it.fecha }

        return Prevision(fecha, saldo, saldosCuentas(cuentas, movs, fecha), minimo, fechaMinimo, aplazables)
    }

    /** Cambia la fecha de los movimientos indicados. */
    fun aplazar(movs: List<Movimiento>, ids: Set<String>, nuevaFecha: LocalDate): List<Movimiento> =
        movs.map { if (it.id in ids) it.copy(fecha = nuevaFecha.toString()) else it }

    /**
     * Propone qué gastos mover a [nuevaFecha] para no quedar en negativo hasta [fecha].
     * En cada paso mira el peor día y aplaza el gasto más grande de antes de ese día: así se mueven
     * los menos gastos posibles. Si no hay forma de cubrir todo, devuelve lo que más se acerca.
     * Solo tiene sentido mover a una fecha posterior a [fecha]; si no, devuelve vacío.
     */
    fun sugerirAplazar(
        cuentas: List<Cuenta>,
        movs: List<Movimiento>,
        hoy: LocalDate,
        fecha: LocalDate,
        nuevaFecha: LocalDate
    ): Set<String> {
        if (!nuevaFecha.isAfter(fecha)) return emptySet()
        val seleccion = mutableSetOf<String>()
        while (true) {
            val p = prevision(cuentas, aplazar(movs, seleccion, nuevaFecha), hoy, fecha)
            if (p.minimo >= 0) return seleccion
            val candidato = p.gastosAplazables
                .filter { it.id !in seleccion && !it.fechaLocal.isAfter(p.fechaMinimo) }
                .maxByOrNull { it.importeCent }
                ?: return seleccion
            seleccion += candidato.id
        }
    }
}
