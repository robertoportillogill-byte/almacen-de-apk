package app.presupuesto

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.presupuesto.logica.Almacen
import app.presupuesto.logica.AnalizadorTexto
import app.presupuesto.logica.Presupuesto
import app.presupuesto.logica.parseImporte
import app.presupuesto.model.Borrador
import app.presupuesto.model.Movimiento
import app.presupuesto.model.Repeticion
import app.presupuesto.ocr.LectorDocumento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Formulario abierto. [editandoId] es null cuando se crea un movimiento nuevo. */
data class Edicion(
    val borrador: Borrador,
    val editandoId: String?,
    val serie: Int,          // nº de filas de la serie del movimiento editado (1 si no es serie)
    val sesion: Int          // cambia cada vez que se rellena desde voz o escaneo, para reiniciar los campos
)

class PresupuestoViewModel(app: Application) : AndroidViewModel(app) {

    private val almacen = Almacen(File(app.filesDir, "presupuesto.json"))

    private val _movs = MutableStateFlow<List<Movimiento>>(emptyList())
    val movs: StateFlow<List<Movimiento>> = _movs.asStateFlow()

    private val _edicion = MutableStateFlow<Edicion?>(null)
    val edicion: StateFlow<Edicion?> = _edicion.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    private val _leyendo = MutableStateFlow(false)
    val leyendo: StateFlow<Boolean> = _leyendo.asStateFlow()

    private var sesion = 0

    init {
        viewModelScope.launch {
            _movs.value = withContext(Dispatchers.IO) { almacen.cargar() }
        }
    }

    fun nuevo(borrador: Borrador = Borrador()) {
        sesion++
        _edicion.value = Edicion(borrador, null, 1, sesion)
    }

    fun editar(m: Movimiento) {
        val serie = if (m.grupo == null) 1 else _movs.value.count { it.grupo == m.grupo }
        _edicion.value = Edicion(
            borrador = Borrador(
                tipo = m.tipo,
                fecha = m.fechaLocal,
                concepto = m.concepto,
                importe = app.presupuesto.logica.formatearImporte(m.importeCent),
                repeticion = m.repeticion
            ),
            editandoId = m.id,
            serie = serie,
            sesion = ++sesion
        )
    }

    fun cerrar() {
        _edicion.value = null
    }

    /** Guarda el formulario. Devuelve false si hay un dato no válido (el mensaje lo explica). */
    fun guardar(b: Borrador): Boolean {
        val cent = parseImporte(b.importe)
        if (cent == null) {
            _mensaje.value = "Escribe un importe válido, por ejemplo 12,50"
            return false
        }
        val concepto = b.concepto.trim().ifEmpty { "Sin concepto" }
        val editandoId = _edicion.value?.editandoId

        if (editandoId == null) {
            val nuevos = Presupuesto.generar(b.copy(concepto = concepto), cent)
            _movs.value = _movs.value + nuevos
            _mensaje.value = when (b.repeticion) {
                Repeticion.NINGUNA -> "Movimiento guardado"
                Repeticion.MENSUAL -> "Guardados ${nuevos.size} meses"
                Repeticion.CUOTAS -> "Guardadas ${nuevos.size} cuotas"
            }
        } else {
            // Editar una fila: se conservan su id, su serie y su posición dentro de ella.
            _movs.value = _movs.value.map { m ->
                if (m.id == editandoId) m.copy(
                    fecha = b.fecha.toString(),
                    concepto = concepto,
                    importeCent = cent,
                    tipo = b.tipo
                ) else m
            }
            _mensaje.value = "Cambios guardados"
        }
        persistir()
        _edicion.value = null
        return true
    }

    /** Borra una fila, o toda su serie si [serie] es true. */
    fun eliminar(serie: Boolean) {
        val ed = _edicion.value ?: return
        val id = ed.editandoId ?: return
        val mov = _movs.value.firstOrNull { it.id == id } ?: return
        _movs.value = if (serie && mov.grupo != null) {
            _movs.value.filterNot { it.grupo == mov.grupo }
        } else {
            _movs.value.filterNot { it.id == id }
        }
        _mensaje.value = if (serie && mov.grupo != null) "Serie eliminada" else "Movimiento eliminado"
        persistir()
        _edicion.value = null
    }

    fun desdeVoz(texto: String) {
        if (texto.isBlank()) return
        val b = AnalizadorTexto.analizarVoz(texto)
        abrirConBorrador(b)
    }

    fun desdeDocumento(texto: String?) {
        if (texto.isNullOrBlank()) {
            _mensaje.value = "No he encontrado texto en la imagen"
            return
        }
        abrirConBorrador(AnalizadorTexto.analizarDocumento(texto))
    }

    fun leerImagen(uri: Uri) {
        _leyendo.value = true
        LectorDocumento.leer(getApplication(), uri) { texto ->
            _leyendo.value = false
            desdeDocumento(texto)
        }
    }

    fun avisoMostrado() {
        _mensaje.value = null
    }

    fun avisar(texto: String) {
        _mensaje.value = texto
    }

    private fun abrirConBorrador(b: Borrador) {
        sesion++
        _edicion.value = Edicion(b, null, 1, sesion)
    }

    private fun persistir() {
        val copia = _movs.value
        viewModelScope.launch(Dispatchers.IO) { almacen.guardar(copia) }
    }
}
