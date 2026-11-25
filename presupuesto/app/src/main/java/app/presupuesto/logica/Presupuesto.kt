package app.presupuesto.logica

import app.presupuesto.model.Borrador
import app.presupuesto.model.Movimiento
import app.presupuesto.model.Repeticion
import java.time.LocalDate
import java.util.UUID

/** Fila lista para pintar: el movimiento y el saldo acumulado después de él. */
data class Fila(val mov: Movimiento, val saldo: Long)

data class Resumen(
    val saldoHoy: Long,       // movimientos con fecha <= hoy
    val saldoPrevisto: Long,  // todos, incluidas las cuotas y meses futuros
    val ingresosMes: Long,
    val egresosMes: Long
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
                total = total
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

    /** Ordena cronológicamente (a igualdad de fecha, respeta el orden de alta) y calcula el saldo corrido. */
    fun conSaldo(movs: List<Movimiento>): List<Fila> {
        var acumulado = 0L
        return movs.sortedBy { it.fecha }.map { m ->
            acumulado += m.importeFirmado
            Fila(m, acumulado)
        }
    }

    fun resumen(movs: List<Movimiento>, hoy: LocalDate): Resumen {
        val mes = java.time.YearMonth.from(hoy)
        val delMes = movs.filter { java.time.YearMonth.from(it.fechaLocal) == mes }
        return Resumen(
            saldoHoy = movs.filter { !it.fechaLocal.isAfter(hoy) }.sumOf { it.importeFirmado },
            saldoPrevisto = movs.sumOf { it.importeFirmado },
            ingresosMes = delMes.filter { it.tipo == app.presupuesto.model.Tipo.INGRESO }.sumOf { it.importeCent },
            egresosMes = delMes.filter { it.tipo == app.presupuesto.model.Tipo.EGRESO }.sumOf { it.importeCent }
        )
    }
}
