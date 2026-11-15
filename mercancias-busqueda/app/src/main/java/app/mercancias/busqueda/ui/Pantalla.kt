package app.mercancias.busqueda.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mercancias.busqueda.MainViewModel
import app.mercancias.busqueda.R
import app.mercancias.busqueda.model.Campo
import app.mercancias.busqueda.model.Estado
import app.mercancias.busqueda.model.Mercancia
import app.mercancias.busqueda.model.filtradas
import app.mercancias.busqueda.model.formatoKilos
import app.mercancias.busqueda.model.ordenadas
import app.mercancias.busqueda.ocr.Dictado
import app.mercancias.busqueda.ocr.FilaLeida
import app.mercancias.busqueda.ocr.LectorImagen
import kotlinx.coroutines.launch
import java.io.File

private class Confirmacion(val titulo: String, val texto: String, val boton: String, val accion: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val contexto = LocalContext.current
    val scope = rememberCoroutineScope()
    val avisos = remember { SnackbarHostState() }

    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var campoBusqueda by rememberSaveable { mutableStateOf<Campo?>(null) }
    var campoOrden by rememberSaveable { mutableStateOf(Campo.ORDEN) }
    var ascendente by rememberSaveable { mutableStateOf(true) }
    var detalleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var fotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    var formulario by remember { mutableStateOf<Mercancia?>(null) }
    var revision by remember { mutableStateOf<List<Mercancia>?>(null) }
    var leyendo by remember { mutableStateOf(false) }
    var menuAnadir by remember { mutableStateOf(false) }
    var menuMas by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<Confirmacion?>(null) }

    fun avisar(texto: String) = scope.launch { avisos.showSnackbar(texto) }

    fun procesar(uri: Uri) {
        scope.launch {
            leyendo = true
            try {
                val filas = LectorImagen.leer(contexto, uri)
                if (filas.isEmpty()) {
                    avisos.showSnackbar(
                        "No he reconocido ninguna fila. Prueba con la hoja entera, bien iluminada y sin sombras.",
                        duration = SnackbarDuration.Long,
                    )
                } else {
                    revision = filas.mapIndexed { i, f -> f.aMercancia(-(i + 1L)) }
                }
            } catch (e: Exception) {
                avisos.showSnackbar("No se pudo leer la hoja: ${e.message ?: "error desconocido"}")
            } finally {
                leyendo = false
            }
        }
    }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { hecha ->
        val uri = fotoUri
        if (hecha && uri != null) procesar(uri)
    }
    val archivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) procesar(uri)
    }
    val dictarFila = rememberDictado { texto -> formulario = Dictado.interpretar(texto) }

    val todas = vm.lista
    val pendientes = todas.filter { it.estado != Estado.ENCONTRADO }
    val encontradas = todas.filter { it.estado == Estado.ENCONTRADO }
    val base = when (pestana) { 0 -> pendientes; 1 -> encontradas; else -> todas }
    val visibles = remember(base, busqueda, campoBusqueda, campoOrden, ascendente) {
        base.filtradas(busqueda, campoBusqueda).ordenadas(campoOrden, ascendente)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mercancías Búsqueda") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = { menuMas = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = menuMas, onDismissRequest = { menuMas = false }) {
                        DropdownMenuItem(text = { Text("Compartir resumen") }, enabled = todas.isNotEmpty(), onClick = {
                            menuMas = false
                            compartir(contexto, resumen(todas))
                        })
                        DropdownMenuItem(text = { Text("Quitar los encontrados") }, enabled = encontradas.isNotEmpty(), onClick = {
                            menuMas = false
                            confirmar = Confirmacion(
                                "Quitar los encontrados",
                                "Se borrarán de la lista las ${encontradas.size} mercancías ya encontradas. Las pendientes se quedan.",
                                "Quitar",
                            ) { vm.quitarEncontradas() }
                        })
                        DropdownMenuItem(text = { Text("Vaciar la lista (hoja nueva)") }, enabled = todas.isNotEmpty(), onClick = {
                            menuMas = false
                            confirmar = Confirmacion(
                                "Vaciar la lista",
                                "Se borrarán las ${todas.size} mercancías, incluidas las pendientes.",
                                "Vaciar",
                            ) { vm.vaciar() }
                        })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(avisos) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { menuAnadir = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Añadir") },
            )
        },
    ) { margen ->
        Column(Modifier.padding(margen).fillMaxSize()) {
            Resumen(todas)
            TabRow(selectedTabIndex = pestana) {
                Tab(pestana == 0, { pestana = 0 }, text = { Text("Pendientes (${pendientes.size})") })
                Tab(pestana == 1, { pestana = 1 }, text = { Text("Encontrados (${encontradas.size})") })
                Tab(pestana == 2, { pestana = 2 }, text = { Text("Todos (${todas.size})") })
            }
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                singleLine = true,
                placeholder = { Text(if (campoBusqueda == null) "Buscar en todos los campos" else "Buscar por ${campoBusqueda!!.etiqueta.lowercase()}") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (busqueda.isNotEmpty()) {
                        IconButton(onClick = { busqueda = "" }) { Icon(Icons.Default.Clear, contentDescription = "Borrar búsqueda") }
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Desplegable(
                    texto = "Buscar en: ${campoBusqueda?.etiqueta ?: "Todo"}",
                    opciones = listOf<Campo?>(null) + Campo.entries,
                    nombre = { it?.etiqueta ?: "Todo" },
                    alElegir = { campoBusqueda = it },
                )
                Desplegable(
                    texto = "Ordenar: ${campoOrden.etiqueta}",
                    opciones = Campo.entries,
                    nombre = { it.etiqueta },
                    alElegir = { campoOrden = it },
                )
                IconButton(onClick = { ascendente = !ascendente }) {
                    Icon(
                        if (ascendente) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (ascendente) "De menor a mayor. Tocar para invertir" else "De mayor a menor. Tocar para invertir",
                    )
                }
            }

            if (visibles.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            todas.isEmpty() -> "La lista está vacía.\nPulsa «Añadir» para escanear la hoja de ruta, escribir a mano o dictar."
                            busqueda.isNotBlank() -> "Nada coincide con «$busqueda»."
                            pestana == 0 -> "No queda nada pendiente."
                            pestana == 1 -> "Todavía no has encontrado ninguna."
                            else -> "Sin mercancías."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibles, key = { it.id }) { m ->
                        Tarjeta(
                            m = m,
                            alAbrir = { detalleId = m.id },
                            alEncontrar = {
                                if (m.bultosLlevar > 1) {
                                    detalleId = m.id
                                } else {
                                    vm.guardar(m.copy(encontrados = m.bultosLlevar))
                                    scope.launch {
                                        val r = avisos.showSnackbar(
                                            "Encontrado: ${m.cliente.ifBlank { m.numOrden }}",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (r == SnackbarResult.ActionPerformed) vm.guardar(m)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // --- Añadir: las cuatro formas de meter datos ---
    if (menuAnadir) {
        ModalBottomSheet(onDismissRequest = { menuAnadir = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text("Añadir mercancías", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                ListItem(
                    headlineContent = { Text("Escanear la hoja con la cámara") },
                    supportingContent = { Text("Lee todas las filas de la hoja de una vez") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_camara), contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuAnadir = false
                        try {
                            val carpeta = File(contexto.cacheDir, "fotos").apply { mkdirs() }
                            val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", File(carpeta, "hoja.jpg"))
                            fotoUri = uri
                            camara.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            avisar("No hay ninguna app de cámara disponible")
                        }
                    },
                )
                ListItem(
                    headlineContent = { Text("Foto o PDF guardado") },
                    supportingContent = { Text("Una foto de la galería o el PDF del escáner") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_imagen), contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuAnadir = false
                        try {
                            archivo.launch(arrayOf("image/*", "application/pdf"))
                        } catch (e: ActivityNotFoundException) {
                            avisar("No hay ningún explorador de archivos disponible")
                        }
                    },
                )
                ListItem(
                    headlineContent = { Text("Escribir a mano") },
                    supportingContent = { Text("Rellenar los campos de una mercancía") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable { menuAnadir = false; formulario = Mercancia() },
                )
                ListItem(
                    headlineContent = { Text("Dictar por voz") },
                    supportingContent = { Text("Di: «cliente …, dirección …, bultos 2 de 1, kilos …»") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null) },
                    modifier = Modifier.clickable {
                        menuAnadir = false
                        dictarFila("Cliente…, dirección…, bultos…, kilos…")
                    },
                )
            }
        }
    }

    // --- Detalle: encontrados, ubicación y observación ---
    val enDetalle = detalleId?.let { id -> todas.firstOrNull { it.id == id } }
    if (enDetalle != null) {
        Detalle(
            m = enDetalle,
            ubicacionesUsadas = todas.map { it.ubicacion }.filter { it.isNotBlank() }.distinct().takeLast(8),
            alGuardar = { vm.guardar(it); detalleId = null },
            alEditar = { detalleId = null; formulario = enDetalle },
            alEliminar = {
                detalleId = null
                confirmar = Confirmacion(
                    "Eliminar mercancía",
                    "Se borrará «${enDetalle.cliente.ifBlank { enDetalle.numOrden }}» de la lista.",
                    "Eliminar",
                ) { vm.eliminar(enDetalle.id) }
            },
            alCerrar = { detalleId = null },
        )
    }

    // --- Revisión de lo leído en la hoja ---
    revision?.let { filas ->
        Revision(
            filas = filas,
            alCambiar = { revision = it },
            alEditar = { formulario = it },
            alCancelar = { revision = null },
            alAceptar = {
                val (nuevas, actualizadas) = vm.anadir(filas.map { it.copy(id = 0) })
                revision = null
                pestana = 0
                avisar(
                    if (actualizadas == 0) "Añadidas $nuevas mercancías"
                    else "Añadidas $nuevas y actualizadas $actualizadas que ya estaban"
                )
            },
        )
    }

    // --- Formulario (alta, edición o corrección de una fila leída) ---
    formulario?.let { m ->
        Formulario(
            inicial = m,
            titulo = when {
                m.id > 0 -> "Editar mercancía"
                m.id < 0 -> "Corregir fila leída"
                else -> "Nueva mercancía"
            },
            alGuardar = { g ->
                if (g.id < 0) {
                    // Id nuevo para que la fila se redibuje con los datos corregidos.
                    revision = revision?.map { if (it.id == g.id) g.copy(id = g.id - 1000) else it }
                } else {
                    vm.guardar(g)
                }
                formulario = null
            },
            alCerrar = { formulario = null },
        )
    }

    confirmar?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(c.titulo) },
            text = { Text(c.texto) },
            confirmButton = {
                TextButton(onClick = {
                    val antes = vm.lista
                    c.accion()
                    confirmar = null
                    scope.launch {
                        val r = avisos.showSnackbar("Hecho", actionLabel = "Deshacer", duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) vm.restaurar(antes)
                    }
                }) { Text(c.boton) }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } },
        )
    }

    if (leyendo) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Leyendo la hoja…")
                }
            }
        }
    }
}

private fun FilaLeida.aMercancia(id: Long) = Mercancia(
    id = id, orden = orden, numOrden = numOrden, cliente = cliente, poblacion = poblacion, direccion = direccion,
    bultosTotal = bultosTotal, bultosLlevar = bultosLlevar, kilos = kilos, revisar = dudosa,
)

@Composable
private fun Resumen(todas: List<Mercancia>) {
    val aLlevar = todas.sumOf { it.bultosLlevar }
    val hallados = todas.sumOf { it.encontrados.coerceAtMost(it.bultosLlevar) }
    val kilos = todas.sumOf { it.kilos }
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            "Bultos encontrados: $hallados de $aLlevar  ·  ${formatoKilos(kilos)} kg en total",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun <T> Desplegable(texto: String, opciones: List<T>, nombre: (T) -> String, alElegir: (T) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { abierto = true },
            label = { Text(texto, maxLines = 1) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { o ->
                DropdownMenuItem(text = { Text(nombre(o)) }, onClick = { abierto = false; alElegir(o) })
            }
        }
    }
}

@Composable
private fun Tarjeta(m: Mercancia, alAbrir: () -> Unit, alEncontrar: () -> Unit) {
    val colorEstado = when (m.estado) {
        Estado.ENCONTRADO -> Verde
        Estado.PARCIAL -> Naranja
        Estado.PENDIENTE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = alAbrir,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOfNotNull(
                        if (m.orden > 0) "Orden ${m.orden}" else null,
                        m.numOrden.takeIf { it.isNotBlank() }?.let { "Nº $it" },
                    ).joinToString("  ·  ").ifEmpty { "Sin nº de orden" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    m.cliente.ifBlank { "(sin cliente)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (m.direccionCompleta.isNotBlank()) {
                    Text(m.direccionCompleta, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    buildString {
                        append(if (m.bultosTotal == m.bultosLlevar) "Bultos: ${m.bultosLlevar}" else "Bultos: llevar ${m.bultosLlevar} de ${m.bultosTotal}")
                        append("  ·  ${formatoKilos(m.kilos)} kg")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (m.ubicacion.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Ubicación", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(" ${m.ubicacion}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                m.avisoFaltan?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Naranja, fontWeight = FontWeight.SemiBold)
                }
                if (m.observacion.isNotBlank()) {
                    Text("Obs.: ${m.observacion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.revisar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, Modifier.size(14.dp), tint = Naranja)
                        Text(" Datos leídos con dudas: revísalos", style = MaterialTheme.typography.bodySmall, color = Naranja)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (m.estado == Estado.ENCONTRADO) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colorEstado, modifier = Modifier.size(32.dp))
                    Text("Encontrado", style = MaterialTheme.typography.labelSmall, color = colorEstado)
                }
            } else {
                FilledTonalButton(onClick = alEncontrar, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(if (m.estado == Estado.PARCIAL) "${m.encontrados}/${m.bultosLlevar}" else "Encontrado")
                }
            }
        }
    }
}

private fun resumen(todas: List<Mercancia>): String = buildString {
    fun linea(m: Mercancia) = buildString {
        append("- ")
        if (m.orden > 0) append("[${m.orden}] ")
        append(m.cliente.ifBlank { m.numOrden })
        append(" · bultos ${m.bultosTexto} · ${formatoKilos(m.kilos)} kg")
        if (m.ubicacion.isNotBlank()) append(" · ubic.: ${m.ubicacion}")
        m.avisoFaltan?.let { append(" · $it") }
        if (m.observacion.isNotBlank()) append(" · obs.: ${m.observacion}")
    }
    val pendientes = todas.filter { it.estado != Estado.ENCONTRADO }.sortedBy { it.orden }
    val encontradas = todas.filter { it.estado == Estado.ENCONTRADO }.sortedBy { it.orden }
    appendLine("MERCANCÍAS · ${encontradas.size} encontradas, ${pendientes.size} pendientes")
    if (pendientes.isNotEmpty()) {
        appendLine()
        appendLine("PENDIENTES")
        pendientes.forEach { appendLine(linea(it)) }
    }
    if (encontradas.isNotEmpty()) {
        appendLine()
        appendLine("ENCONTRADAS")
        encontradas.forEach { appendLine(linea(it)) }
    }
}.trim()

private fun compartir(contexto: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    contexto.startActivity(Intent.createChooser(envio, "Compartir resumen"))
}
