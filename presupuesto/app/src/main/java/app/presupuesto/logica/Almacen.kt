package app.presupuesto.logica

import app.presupuesto.model.Movimiento
import kotlinx.serialization.json.Json
import java.io.File

/** Guarda los movimientos en un único JSON dentro del almacenamiento privado de la app. */
class Almacen(private val archivo: File) {

    private val json = Json { ignoreUnknownKeys = true }

    fun cargar(): List<Movimiento> {
        if (!archivo.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<Movimiento>>(archivo.readText())
        }.getOrDefault(emptyList())
    }

    /** Escribe en un fichero temporal y lo renombra: si la app se cierra a mitad, no se pierde el archivo anterior. */
    fun guardar(movs: List<Movimiento>) {
        val tmp = File(archivo.parentFile, archivo.name + ".tmp")
        tmp.writeText(json.encodeToString(movs))
        if (!tmp.renameTo(archivo)) {
            archivo.delete()
            tmp.renameTo(archivo)
        }
    }
}
