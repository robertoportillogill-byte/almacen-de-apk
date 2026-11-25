package app.presupuesto.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.presupuesto.PresupuestoViewModel
import app.presupuesto.R
import app.presupuesto.logica.Fila
import app.presupuesto.logica.Presupuesto
import app.presupuesto.logica.Resumen
import app.presupuesto.model.Datos
import app.presupuesto.model.Movimiento
import app.presupuesto.model.Repeticion
import app.presupuesto.model.Tipo
import app.presupuesto.model.TipoCuenta
import app.presupuesto.model.nombre
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Espanol = Locale("es", "ES")
private val FormatoDia = DateTimeFormatter.ofPattern("d MMM", Espanol)
private val FormatoMes = DateTimeFormatter.ofPattern("MMMM yyyy", Espanol)

private enum class Pestana(val titulo: String, val icono: ImageVector) {
    MOVIMIENTOS("Movimientos", Icons.Filled.List),
    CUENTAS("Cuentas", Icons.Filled.AccountBox),
    PREVISION("Previsión", Icons.Filled.DateRange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipal(vm: PresupuestoViewModel) {
    val contexto = LocalContext.current
    val datos by vm.datos.collectAsStateWithLifecycle()
    val edicion by vm.edicion.collectAsStateWithLifecycle()
    val edicionCuenta by vm.edicionCuenta.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val leyendo by vm.leyendo.collectAsStateWithLifecycle()

    var pestana by rememberSaveable { mutableIntStateOf(0) }
    val actual = Pestana.entries[pestana]

    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(mensaje) {
        mensaje?.let {
            avisos.showSnackbar(it)
            vm.avisoMostrado()
        }
    }

    // ---- Entradas: voz, cámara y galería ----
    var uriFoto by remember { mutableStateOf<Uri?>(null) }

    val lanzarCamara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) uriFoto?.let { vm.leerImagen(it) }
    }
    val lanzarGaleria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.leerImagen(it) }
    }
    val lanzarVoz = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.desdeVoz(it) }
    }

    val onVoz: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el movimiento: gasto o ingreso, importe, concepto y con qué pagas")
        }
        try {
            lanzarVoz.launch(intent)
        } catch (e: ActivityNotFoundException) {
            vm.avisar("Este móvil no tiene reconocimiento de voz")
        }
    }
    val onEscanear: () -> Unit = {
        val archivo = File(contexto.cacheDir, "fotos/documento_${System.currentTimeMillis()}.jpg")
            .apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fotos", archivo)
        uriFoto = uri
        try {
            lanzarCamara.launch(uri)
        } catch (e: ActivityNotFoundException) {
            vm.avisar("No hay aplicación de cámara disponible")
        }
    }
    val onGaleria: () -> Unit = {
        lanzarGaleria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(avisos) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Pestana.entries.forEachIndexed { i, p ->
                    NavigationBarItem(
                        selected = i == pestana,
                        onClick = { pestana = i },
                        icon = { Icon(p.icono, contentDescription = null) },
                        label = { Text(p.titulo) }
                    )
                }
            }
        },
        floatingActionButton = {
            when (actual) {
                Pestana.MOVIMIENTOS -> ExtendedFloatingActionButton(
                    onClick = { vm.nuevo() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nuevo", fontWeight = FontWeight.SemiBold) }
                )
                Pestana.CUENTAS -> ExtendedFloatingActionButton(
                    onClick = { vm.nuevaCuenta(TipoCuenta.BANCO) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nueva cuenta", fontWeight = FontWeight.SemiBold) }
                )
                Pestana.PREVISION -> Unit
            }
        }
    ) { relleno ->
        when (actual) {
            Pestana.MOVIMIENTOS -> ListaMovimientos(datos, leyendo, relleno) { vm.editar(it) }
            Pestana.CUENTAS -> PantallaCuentas(datos, relleno, onNueva = vm::nuevaCuenta, onEditar = vm::editarCuenta)
            Pestana.PREVISION -> PantallaPrevision(datos, relleno, onMover = vm::moverGastos)
        }
    }

    edicion?.let { ed ->
        val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { vm.cerrar() },
            sheetState = estado,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Formulario(
                edicion = ed,
                cuentas = datos.cuentas,
                onGuardar = { vm.guardar(it) },
                onEliminar = { serie -> vm.eliminar(serie) },
                onVoz = onVoz,
                onEscanear = onEscanear,
                onGaleria = onGaleria
            )
        }
    }

    edicionCuenta?.let { ec ->
        val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { vm.cerrarCuenta() },
            sheetState = estado,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            FormularioCuenta(
                edicion = ec,
                onGuardar = { n, t, s -> vm.guardarCuenta(n, t, s) },
                onEliminar = { vm.eliminarCuenta() }
            )
        }
    }
}

@Composable
private fun ListaMovimientos(
    datos: Datos,
    leyendo: Boolean,
    relleno: PaddingValues,
    onEditar: (Movimiento) -> Unit
) {
    val hoy = remember { LocalDate.now() }
    val resumen = remember(datos) { Presupuesto.resumen(datos.cuentas, datos.movimientos, hoy) }
    val porTipo = remember(datos) {
        Presupuesto.totalesPorTipo(Presupuesto.saldosCuentas(datos.cuentas, datos.movimientos, hoy))
    }
    val porMes = remember(datos) {
        Presupuesto.conSaldo(datos.movimientos, Presupuesto.saldoInicial(datos.cuentas))
            .groupBy { YearMonth.from(it.mov.fechaLocal) }
            .toSortedMap(compareByDescending { it })
    }
    val nombreCuenta = remember(datos) { datos.cuentas.associate { it.id to it.nombre } }

    LazyColumn(
        contentPadding = rellenoLista(relleno),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item { Cabecera(resumen, porTipo) }

        if (leyendo) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Leyendo el documento…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }

        if (datos.movimientos.isEmpty()) {
            item { Vacio() }
        }

        porMes.forEach { (mes, filasMes) ->
            item(key = "mes-$mes") { CabeceraMes(mes, filasMes.map { it.mov }) }
            items(filasMes.reversed(), key = { it.mov.id }) { fila ->
                FilaMovimiento(fila, nombreCuenta[fila.mov.cuentaId].orEmpty()) { onEditar(fila.mov) }
            }
        }
    }
}

@Composable
private fun Cabecera(r: Resumen, porTipo: Map<TipoCuenta, Long>) {
    val mes = YearMonth.now().format(FormatoMes).replaceFirstChar { it.uppercase() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(FormaTarjeta)
            .background(fondoCabecera())
            .padding(22.dp)
    ) {
        Text("Total general hoy", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
        Text(moneda(r.saldoHoy), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Previsto con lo programado: ${moneda(r.saldoPrevisto)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(14.dp))
        TotalesPorTipo(porTipo)
        Spacer(Modifier.height(14.dp))
        Text(mes, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            Estadistica("Ingresos", moneda(r.ingresosMes), Modifier.weight(1f))
            Estadistica("Gastos", moneda(r.egresosMes), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Estadistica(titulo: String, valor: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(12.dp)
    ) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun CabeceraMes(mes: YearMonth, movs: List<Movimiento>) {
    val ingresos = movs.filter { it.tipo == Tipo.INGRESO }.sumOf { it.importeCent }
    val egresos = movs.filter { it.tipo == Tipo.EGRESO }.sumOf { it.importeCent }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            mes.format(FormatoMes).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("+ ${moneda(ingresos)}", style = MaterialTheme.typography.labelMedium, color = colorIngreso())
            Text("− ${moneda(egresos)}", style = MaterialTheme.typography.labelMedium, color = colorEgreso())
        }
    }
}

@Composable
private fun FilaMovimiento(fila: Fila, cuenta: String, onClick: () -> Unit) {
    val m = fila.mov
    val esIngreso = m.tipo == Tipo.INGRESO
    val color = if (esIngreso) colorIngreso() else colorEgreso()
    val etiqueta = when (m.repeticion) {
        Repeticion.NINGUNA -> null
        Repeticion.MENSUAL -> "Mensual ${m.indice}/${m.total}"
        Repeticion.CUOTAS -> "Cuota ${m.indice}/${m.total}"
    }

    Card(
        onClick = onClick,
        shape = FormaTarjeta,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (esIngreso) "+" else "−", color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.concepto,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        listOf(m.fechaLocal.format(FormatoDia), cuenta).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (etiqueta != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                etiqueta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (esIngreso) "+" else "−") + moneda(m.importeCent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.End
                )
                Text(
                    "Saldo ${moneda(fila.saldo)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Vacio() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(R.drawable.ic_imagen),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Aún no hay movimientos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Primero crea tus cuentas (caja, bancos y tarjetas) en la pestaña «Cuentas». " +
                "Después pulsa «Nuevo» para escribir, dictar por voz o escanear un documento.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
