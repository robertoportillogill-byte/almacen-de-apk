package app.mercancias.busqueda

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mercancias.busqueda.data.Almacen
import app.mercancias.busqueda.model.Mercancia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val almacen = Almacen(File(app.filesDir, "mercancias.json"))

    var lista by mutableStateOf(almacen.cargar())
        private set

    private fun cambiar(nueva: List<Mercancia>) {
        lista = nueva
        viewModelScope.launch(Dispatchers.IO) { almacen.guardar(nueva) }
    }

    private fun nuevoId(usados: List<Mercancia>): Long = (usados.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(0L) + 1

    /** Crea (id <= 0) o actualiza una mercancía. */
    fun guardar(m: Mercancia) {
        val limpia = m.normalizada()
        cambiar(
            if (limpia.id > 0 && lista.any { it.id == limpia.id }) lista.map { if (it.id == limpia.id) limpia else it }
            else lista + limpia.copy(id = nuevoId(lista))
        )
    }

    /**
     * Añade filas leídas de una hoja. Si un nº de orden ya estaba en la lista se actualizan sus
     * datos pero se conserva lo ya trabajado (encontrados, ubicación, observación).
     * Devuelve (nuevas, actualizadas).
     */
    fun anadir(filas: List<Mercancia>): Pair<Int, Int> {
        val resultado = lista.toMutableList()
        var nuevas = 0
        var actualizadas = 0
        for (f in filas.map { it.normalizada() }) {
            val i = if (f.numOrden.isBlank()) -1 else resultado.indexOfFirst { it.numOrden == f.numOrden }
            if (i >= 0) {
                val v = resultado[i]
                resultado[i] = f.copy(
                    id = v.id,
                    encontrados = v.encontrados.coerceAtMost(f.bultosLlevar),
                    ubicacion = v.ubicacion.ifBlank { f.ubicacion },
                    observacion = v.observacion.ifBlank { f.observacion },
                )
                actualizadas++
            } else {
                resultado += f.copy(id = nuevoId(resultado))
                nuevas++
            }
        }
        cambiar(resultado)
        return nuevas to actualizadas
    }

    fun eliminar(id: Long) = cambiar(lista.filterNot { it.id == id })

    fun quitarEncontradas() = cambiar(lista.filter { it.encontrados < it.bultosLlevar })

    fun vaciar() = cambiar(emptyList())

    fun restaurar(anterior: List<Mercancia>) = cambiar(anterior)

    private fun Mercancia.normalizada(): Mercancia {
        val total = bultosTotal.coerceAtLeast(1)
        val llevar = bultosLlevar.coerceAtLeast(1)
        return copy(
            numOrden = numOrden.trim(), cliente = cliente.trim(), poblacion = poblacion.trim(),
            direccion = direccion.trim(), ubicacion = ubicacion.trim(), observacion = observacion.trim(),
            bultosTotal = total, bultosLlevar = llevar,
            encontrados = encontrados.coerceIn(0, llevar), kilos = kilos.coerceAtLeast(0.0),
        )
    }
}
