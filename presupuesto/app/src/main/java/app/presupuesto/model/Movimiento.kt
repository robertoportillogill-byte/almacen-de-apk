package app.presupuesto.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class Tipo { INGRESO, EGRESO }

/** Único, recurrente mensual, o pagado en cuotas. */
@Serializable
enum class Repeticion { NINGUNA, MENSUAL, CUOTAS }

/** Una línea del presupuesto. El importe va siempre en céntimos y en positivo; el tipo marca el signo. */
@Serializable
data class Movimiento(
    val id: String,
    val fecha: String,                  // yyyy-MM-dd
    val concepto: String,
    val importeCent: Long,
    val tipo: Tipo,
    val repeticion: Repeticion = Repeticion.NINGUNA,
    val grupo: String? = null,          // id común de todas las filas de una misma serie
    val indice: Int = 1,                // nº de cuota o de mes dentro de la serie
    val total: Int = 1                  // nº total de cuotas o meses de la serie
) {
    val fechaLocal: LocalDate get() = LocalDate.parse(fecha)
    val importeFirmado: Long get() = if (tipo == Tipo.INGRESO) importeCent else -importeCent
}

/** Lo que el usuario está escribiendo en el formulario (o lo que han extraído la voz o el escaneo). */
data class Borrador(
    val tipo: Tipo = Tipo.EGRESO,
    val fecha: LocalDate = LocalDate.now(),
    val concepto: String = "",
    val importe: String = "",           // texto tal como se escribe, p. ej. "1.234,56"
    val repeticion: Repeticion = Repeticion.NINGUNA,
    val veces: Int = 12                 // meses (recurrente) o cuotas
)
