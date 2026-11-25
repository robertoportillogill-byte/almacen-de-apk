package app.presupuesto.logica

import app.presupuesto.model.Datos
import app.presupuesto.model.Movimiento
import kotlinx.serialization.json.Json
import java.io.File

/** Guarda cuentas y movimientos en un único JSON dentro del almacenamiento privado de la app. */
class Almacen(private val archivo: File) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lee el formato actual ({cuentas, movimientos}) o el de la versión 1.0 (solo la lista de movimientos).
     * Si el archivo no se puede leer, se guarda una copia aparte antes de empezar de cero, para no perder nada.
     */
    fun cargar(): Datos {
        if (!archivo.exists()) return Datos()
        val texto = archivo.readText()
        runCatching { return json.decodeFromString<Datos>(texto) }
        runCatching { return Datos(movimientos = json.decodeFromString<List<Movimiento>>(texto)) }
        archivo.copyTo(File(archivo.parentFile, archivo.name + ".ilegible"), overwrite = true)
        return Datos()
    }

    /** Escribe en un fichero temporal y lo renombra: si la app se cierra a mitad, no se pierde el archivo anterior. */
    fun guardar(datos: Datos) {
        val tmp = File(archivo.parentFile, archivo.name + ".tmp")
        tmp.writeText(json.encodeToString(datos))
        if (!tmp.renameTo(archivo)) {
            archivo.delete()
            tmp.renameTo(archivo)
        }
    }
}
