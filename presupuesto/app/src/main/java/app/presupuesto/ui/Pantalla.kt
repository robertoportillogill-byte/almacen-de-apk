package app.presupuesto.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import app.presupuesto.model.Repeticion
import app.presupuesto.model.Tipo
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Espanol = Locale("es", "ES")
private val FormatoDia = DateTimeFormatter.ofPattern("d MMM", Espanol)
private val FormatoMes = DateTimeFormatter.ofPattern("MMMM yyyy", Espanol)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipal(vm: PresupuestoViewModel) {
    val contexto = LocalContext.current
    val movs by vm.movs.collectAsStateWithLifecycle()
    val edicion by vm.edicion.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val leyendo by vm.leyendo.collectAsStateWithLifecycle()

    val hoy = remember { LocalDate.now() }
    val resumen = remember(movs) { Presupuesto.resumen(movs, hoy) }
    val porMes = remember(movs) {
        Presupuesto.conSaldo(movs)
            .groupBy { YearMonth.from(it.mov.fechaLocal) }
            .toSortedMap(compareByDescending { it })
    }

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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el movimiento: gasto o ingreso, importe y concepto")
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.nuevo() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null) },
                text = { Text("Nuevo", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { relleno ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = relleno.calculateTopPadding() + 12.dp,
                bottom = relleno.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item { Cabecera(resumen.saldoHoy, resumen.saldoPrevisto, resumen.ingresosMes, resumen.egresosMes) }

            if (leyendo) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Leyendo el documento…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }

            if (movs.isEmpty()) {
                item { Vacio() }
            }

            porMes.forEach { (mes, filasMes) ->
                item(key = "mes-$mes") { CabeceraMes(mes, filasMes.map { it.mov }) }
                items(filasMes.reversed(), key = { it.mov.id }) { fila ->
                    FilaMovimiento(fila) { vm.editar(fila.mov) }
                }
            }
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
                onGuardar = { vm.guardar(it) },
                onEliminar = { serie -> vm.eliminar(serie) },
                onVoz = onVoz,
                onEscanear = onEscanear,
                onGaleria = onGaleria
            )
        }
    }
}

@Composable
private fun Cabecera(saldoHoy: Long, previsto: Long, ingresos: Long, egresos: Long) {
    val hoy = LocalDate.now()
    val mes = YearMonth.from(hoy).format(FormatoMes).replaceFirstChar { it.uppercase() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(FormaTarjeta)
            .background(fondoCabecera())
            .padding(22.dp)
    ) {
        Text("Saldo de hoy", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
        Text(
            moneda(saldoHoy),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Previsto con lo programado: ${moneda(previsto)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(18.dp))
        Text(mes, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            Estadistica("Ingresos", moneda(ingresos), Modifier.weight(1f))
            Estadistica("Gastos", moneda(egresos), Modifier.weight(1f))
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
private fun CabeceraMes(mes: YearMonth, movs: List<app.presupuesto.model.Movimiento>) {
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
private fun FilaMovimiento(fila: Fila, onClick: () -> Unit) {
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
                        m.fechaLocal.format(FormatoDia),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            "Pulsa «Nuevo» para escribir, dictar por voz o escanear un documento.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
