package app.mercancias.busqueda.data

import app.mercancias.busqueda.model.Mercancia
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class Datos(val version: Int = 1, val mercancias: List<Mercancia> = emptyList())

/** Guarda la lista en un archivo JSON dentro del almacenamiento privado de la app. */
class Almacen(private val archivo: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun cargar(): List<Mercancia> = try {
        if (archivo.exists()) json.decodeFromString<Datos>(archivo.readText()).mercancias else emptyList()
    } catch (e: Exception) {
        // Si el archivo está dañado se conserva una copia en vez de perderlo sin más.
        runCatching { archivo.copyTo(File(archivo.parentFile, archivo.name + ".dañado"), overwrite = true) }
        emptyList()
    }

    @Synchronized
    fun guardar(lista: List<Mercancia>) {
        val tmp = File(archivo.parentFile, archivo.name + ".tmp")
        tmp.writeText(json.encodeToString(Datos.serializer(), Datos(mercancias = lista)))
        if (!tmp.renameTo(archivo)) {
            tmp.copyTo(archivo, overwrite = true)
            tmp.delete()
        }
    }
}
