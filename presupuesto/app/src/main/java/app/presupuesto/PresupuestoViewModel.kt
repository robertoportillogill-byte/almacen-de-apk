package app.presupuesto

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.presupuesto.logica.Almacen
import app.presupuesto.logica.AnalizadorTexto
import app.presupuesto.logica.Presupuesto
import app.presupuesto.logica.formatearImporte
import app.presupuesto.logica.parseImporte
import app.presupuesto.logica.parseSaldo
import app.presupuesto.model.Borrador
import app.presupuesto.model.Cuenta
import app.presupuesto.model.Datos
import app.presupuesto.model.Movimiento
import app.presupuesto.model.Repeticion
import app.presupuesto.model.TipoCuenta
import app.presupuesto.ocr.LectorDocumento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Formulario de movimiento abierto. [editandoId] es null cuando se crea uno nuevo. */
data class Edicion(
    val borrador: Borrador,
    val editandoId: String?,
    val serie: Int,          // nº de filas de la serie del movimiento editado (1 si no es serie)
    val sesion: Int          // cambia cada vez que se rellena desde voz o escaneo, para reiniciar los campos
)

/** Formulario de cuenta abierto. [cuenta] es null cuando se crea una nueva. */
data class EdicionCuenta(
    val cuenta: Cuenta?,
    val tipoInicial: TipoCuenta,
    val sesion: Int
)

class PresupuestoViewModel(app: Application) : AndroidViewModel(app) {

    private val almacen = Almacen(File(app.filesDir, "presupuesto.json"))

    private val _datos = MutableStateFlow(Datos())
    val datos: StateFlow<Datos> = _datos.asStateFlow()

    private val _cargado = MutableStateFlow(false)
    val cargado: StateFlow<Boolean> = _cargado.asStateFlow()

    private val _edicion = MutableStateFlow<Edicion?>(null)
    val edicion: StateFlow<Edicion?> = _edicion.asStateFlow()

    private val _edicionCuenta = MutableStateFlow<EdicionCuenta?>(null)
    val edicionCuenta: StateFlow<EdicionCuenta?> = _edicionCuenta.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    private val _leyendo = MutableStateFlow(false)
    val leyendo: StateFlow<Boolean> = _leyendo.asStateFlow()

    private var sesion = 0
    private var ultimaCuenta: String? = null

    init {
        viewModelScope.launch {
            val cargados = withContext(Dispatchers.IO) { almacen.cargar() }
            val listos = Presupuesto.asegurarCuentas(cargados)
            _datos.value = listos
            _cargado.value = true
            if (listos != cargados) persistir()
        }
    }

    // ---------- Movimientos ----------

    fun nuevo() {
        abrirConBorrador(Borrador(cuentaId = cuentaPorDefecto()?.id))
    }

    fun editar(m: Movimiento) {
        val serie = if (m.grupo == null) 1 else _datos.value.movimientos.count { it.grupo == m.grupo }
        _edicion.value = Edicion(
            borrador = Borrador(
                tipo = m.tipo,
                fecha = m.fechaLocal,
                concepto = m.concepto,
                importe = formatearImporte(m.importeCent),
                repeticion = m.repeticion,
                cuentaId = m.cuentaId
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
        val d = _datos.value
        val cuentaId = b.cuentaId?.takeIf { id -> d.cuentas.any { it.id == id } } ?: cuentaPorDefecto()?.id
        val concepto = b.concepto.trim().ifEmpty { "Sin concepto" }
        val editandoId = _edicion.value?.editandoId
        ultimaCuenta = cuentaId

        if (editandoId == null) {
            val nuevos = Presupuesto.generar(b.copy(concepto = concepto, cuentaId = cuentaId), cent)
            _datos.value = d.copy(movimientos = d.movimientos + nuevos)
            _mensaje.value = when (b.repeticion) {
                Repeticion.NINGUNA -> "Movimiento guardado"
                Repeticion.MENSUAL -> "Guardados ${nuevos.size} meses"
                Repeticion.CUOTAS -> "Guardadas ${nuevos.size} cuotas"
            }
        } else {
            // Editar una fila: se conservan su id, su serie y su posición dentro de ella.
            _datos.value = d.copy(movimientos = d.movimientos.map { m ->
                if (m.id == editandoId) m.copy(
                    fecha = b.fecha.toString(),
                    concepto = concepto,
                    importeCent = cent,
                    tipo = b.tipo,
                    cuentaId = cuentaId
                ) else m
            })
            _mensaje.value = "Cambios guardados"
        }
        persistir()
        _edicion.value = null
        return true
    }

    /** Borra una fila, o toda su serie si [serie] es true. */
    fun eliminar(serie: Boolean) {
        val id = _edicion.value?.editandoId ?: return
        val d = _datos.value
        val mov = d.movimientos.firstOrNull { it.id == id } ?: return
        val porSerie = serie && mov.grupo != null
        _datos.value = d.copy(movimientos = d.movimientos.filterNot {
            if (porSerie) it.grupo == mov.grupo else it.id == id
        })
        _mensaje.value = if (porSerie) "Serie eliminada" else "Movimiento eliminado"
        persistir()
        _edicion.value = null
    }

    /** Pasa los gastos indicados a otra fecha (desde la pantalla de previsión). */
    fun moverGastos(ids: Set<String>, nuevaFecha: LocalDate) {
        if (ids.isEmpty()) return
        val d = _datos.value
        _datos.value = d.copy(movimientos = Presupuesto.aplazar(d.movimientos, ids, nuevaFecha))
        val fecha = nuevaFecha.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
        _mensaje.value = if (ids.size == 1) "1 gasto movido al $fecha" else "${ids.size} gastos movidos al $fecha"
        persistir()
    }

    // ---------- Voz y escaneo ----------

    fun desdeVoz(texto: String) {
        if (texto.isBlank()) return
        abrirConBorrador(conCuenta(AnalizadorTexto.analizarVoz(texto), texto))
    }

    fun desdeDocumento(texto: String?) {
        if (texto.isNullOrBlank()) {
            _mensaje.value = "No he encontrado texto en la imagen"
            return
        }
        abrirConBorrador(conCuenta(AnalizadorTexto.analizarDocumento(texto), texto))
    }

    fun leerImagen(uri: Uri) {
        _leyendo.value = true
        LectorDocumento.leer(getApplication(), uri) { texto ->
            _leyendo.value = false
            desdeDocumento(texto)
        }
    }

    /**
     * Elige la cuenta: primero por nombre dicho o impreso («Santander»), luego por tipo
     * («con tarjeta», «en efectivo»), y si no, la última usada.
     */
    private fun conCuenta(b: Borrador, texto: String): Borrador {
        val cuentas = _datos.value.cuentas
        val porNombre = cuentas
            .filter { it.nombre.length >= 3 && texto.contains(it.nombre, ignoreCase = true) }
            .maxByOrNull { it.nombre.length }
        val elegida = porNombre
            ?: b.tipoCuenta?.let { t -> cuentas.firstOrNull { it.tipo == t } }
            ?: cuentaPorDefecto()
        val concepto = porNombre?.let {
            b.concepto.replace(it.nombre, "", ignoreCase = true).replace(Regex("\\s+"), " ").trim()
                .replaceFirstChar { c -> c.uppercase() }
        } ?: b.concepto
        return b.copy(cuentaId = elegida?.id, concepto = concepto)
    }

    // ---------- Cuentas ----------

    fun nuevaCuenta(tipo: TipoCuenta) {
        _edicionCuenta.value = EdicionCuenta(null, tipo, ++sesion)
    }

    fun editarCuenta(c: Cuenta) {
        _edicionCuenta.value = EdicionCuenta(c, c.tipo, ++sesion)
    }

    fun cerrarCuenta() {
        _edicionCuenta.value = null
    }

    /** Devuelve un texto de error, o null si se ha guardado. */
    fun guardarCuenta(nombre: String, tipo: TipoCuenta, saldoInicial: String): String? {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) return "Pon un nombre, por ejemplo «Santander» o «Visa»"
        val cent = parseSaldo(saldoInicial) ?: return "Revisa el saldo inicial, por ejemplo 250,00 o -120,00"
        val d = _datos.value
        val editando = _edicionCuenta.value?.cuenta
        if (d.cuentas.any { it.nombre.equals(limpio, ignoreCase = true) && it.id != editando?.id }) {
            return "Ya tienes una cuenta con ese nombre"
        }
        _datos.value = if (editando == null) {
            d.copy(cuentas = d.cuentas + Cuenta(UUID.randomUUID().toString(), limpio, tipo, cent))
        } else {
            d.copy(cuentas = d.cuentas.map {
                if (it.id == editando.id) it.copy(nombre = limpio, tipo = tipo, saldoInicialCent = cent) else it
            })
        }
        _mensaje.value = if (editando == null) "Cuenta «$limpio» creada" else "Cuenta guardada"
        persistir()
        _edicionCuenta.value = null
        return null
    }

    /** Devuelve un texto de error, o null si se ha eliminado. */
    fun eliminarCuenta(): String? {
        val c = _edicionCuenta.value?.cuenta ?: return null
        val d = _datos.value
        if (d.cuentas.size <= 1) return "Necesitas al menos una cuenta"
        val usados = d.movimientos.count { it.cuentaId == c.id }
        if (usados > 0) return "Tiene $usados movimientos. Pásalos a otra cuenta o bórralos antes"
        _datos.value = d.copy(cuentas = d.cuentas.filterNot { it.id == c.id })
        if (ultimaCuenta == c.id) ultimaCuenta = null
        _mensaje.value = "Cuenta «${c.nombre}» eliminada"
        persistir()
        _edicionCuenta.value = null
        return null
    }

    // ---------- Avisos ----------

    fun avisoMostrado() {
        _mensaje.value = null
    }

    fun avisar(texto: String) {
        _mensaje.value = texto
    }

    private fun cuentaPorDefecto(): Cuenta? {
        val cuentas = _datos.value.cuentas
        return cuentas.firstOrNull { it.id == ultimaCuenta } ?: cuentas.firstOrNull()
    }

    private fun abrirConBorrador(b: Borrador) {
        _edicion.value = Edicion(b, null, 1, ++sesion)
    }

    private val cerrojo = Any()

    /** Cada escritura guarda el estado más reciente, así el último guardado nunca lo pisa uno anterior. */
    private fun persistir() {
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(cerrojo) { almacen.guardar(_datos.value) }
        }
    }
}
