package app.presupuesto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presupuesto.Edicion
import app.presupuesto.R
import app.presupuesto.logica.parseImporte
import app.presupuesto.logica.formatearImporte
import app.presupuesto.model.Borrador
import app.presupuesto.model.Repeticion
import app.presupuesto.model.Tipo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FormatoFecha: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")

/**
 * Formulario de alta y edición.
 * Alta: escribir, dictar (voz), escanear con la cámara o elegir una foto. Edición: solo la fila (o toda la serie).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Formulario(
    edicion: Edicion?,
    onGuardar: (Borrador) -> Boolean,
    onEliminar: (serie: Boolean) -> Unit,
    onVoz: () -> Unit,
    onEscanear: () -> Unit,
    onGaleria: () -> Unit
) {
    if (edicion == null) return
    // Cada vez que llega un borrador nuevo (voz o escaneo) se reinician los campos.
    key(edicion.sesion) {
        val inicial = edicion.borrador
        val nueva = edicion.editandoId == null

        var tipo by remember { mutableStateOf(inicial.tipo) }
        var concepto by remember { mutableStateOf(inicial.concepto) }
        var importe by remember { mutableStateOf(inicial.importe) }
        var fechaTexto by remember { mutableStateOf(inicial.fecha.format(FormatoFecha)) }
        var repeticion by remember { mutableStateOf(inicial.repeticion) }
        var veces by remember { mutableStateOf(inicial.veces.toString()) }
        var error by remember { mutableStateOf<String?>(null) }

        val fecha = runCatching { LocalDate.parse(fechaTexto.trim(), FormatoFecha) }.getOrNull()
        val cent = parseImporte(importe)
        val numVeces = veces.toIntOrNull()?.takeIf { it in 1..360 }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                if (nueva) "Nuevo movimiento" else "Editar movimiento",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (nueva) {
                Spacer(Modifier.height(14.dp))
                Text("Cargar por", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metodo("Voz", painterResource(R.drawable.ic_mic), onVoz, Modifier.weight(1f))
                    Metodo("Escanear", painterResource(R.drawable.ic_camara), onEscanear, Modifier.weight(1f))
                    Metodo("Foto", painterResource(R.drawable.ic_imagen), onGaleria, Modifier.weight(1f))
                }
                Text(
                    "Por voz di, por ejemplo: «gasto 600 euros televisor en 6 cuotas». Al escanear, la app lee la fecha, el concepto y el total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoBoton("Ingreso", tipo == Tipo.INGRESO, colorIngreso(), Modifier.weight(1f)) {
                    tipo = Tipo.INGRESO
                    if (repeticion == Repeticion.CUOTAS) repeticion = Repeticion.NINGUNA
                }
                TipoBoton("Gasto", tipo == Tipo.EGRESO, colorEgreso(), Modifier.weight(1f)) {
                    tipo = Tipo.EGRESO
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = concepto,
                onValueChange = { concepto = it },
                label = { Text("Concepto") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = importe,
                onValueChange = { importe = it },
                label = { Text(if (repeticion == Repeticion.CUOTAS) "Importe total" else "Importe") },
                suffix = { Text("€") },
                singleLine = true,
                isError = importe.isNotBlank() && cent == null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = fechaTexto,
                onValueChange = { fechaTexto = it },
                label = { Text("Fecha (dd/mm/aaaa)") },
                singleLine = true,
                isError = fechaTexto.isNotBlank() && fecha == null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                AssistChip(onClick = { fechaTexto = LocalDate.now().format(FormatoFecha) }, label = { Text("Hoy") })
                AssistChip(onClick = { fechaTexto = LocalDate.now().minusDays(1).format(FormatoFecha) }, label = { Text("Ayer") })
            }

            if (nueva) {
                Spacer(Modifier.height(18.dp))
                Text("¿Se repite?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = repeticion == Repeticion.NINGUNA,
                        onClick = { repeticion = Repeticion.NINGUNA },
                        label = { Text("Una vez") }
                    )
                    FilterChip(
                        selected = repeticion == Repeticion.MENSUAL,
                        onClick = { repeticion = Repeticion.MENSUAL },
                        label = { Text("Mensual") }
                    )
                    if (tipo == Tipo.EGRESO) {
                        FilterChip(
                            selected = repeticion == Repeticion.CUOTAS,
                            onClick = { repeticion = Repeticion.CUOTAS },
                            label = { Text("En cuotas") }
                        )
                    }
                }

                if (repeticion != Repeticion.NINGUNA) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = veces,
                        onValueChange = { veces = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text(if (repeticion == Repeticion.CUOTAS) "Número de cuotas" else "Durante cuántos meses") },
                        singleLine = true,
                        isError = numVeces == null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    val resumen = when {
                        cent == null || numVeces == null -> null
                        repeticion == Repeticion.CUOTAS -> {
                            val cuota = cent / numVeces
                            "$numVeces cuotas de ${formatearImporte(cuota)} € (desde ${fecha?.format(FormatoFecha) ?: "la fecha indicada"}, una al mes)"
                        }
                        else -> "Se guardarán $numVeces movimientos de ${formatearImporte(cent)} €, uno cada mes"
                    }
                    resumen?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            } else if (edicion.serie > 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Forma parte de una serie de ${edicion.serie} movimientos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    error = when {
                        fecha == null -> "Revisa la fecha (dd/mm/aaaa)"
                        cent == null -> "Revisa el importe, por ejemplo 12,50"
                        repeticion != Repeticion.NINGUNA && numVeces == null -> "Indica un número entre 1 y 360"
                        else -> {
                            val ok = onGuardar(
                                Borrador(
                                    tipo = tipo,
                                    fecha = fecha,
                                    concepto = concepto,
                                    importe = importe,
                                    repeticion = repeticion,
                                    veces = numVeces ?: 1
                                )
                            )
                            if (ok) null else "No se ha podido guardar"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Guardar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            if (!nueva) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onEliminar(false) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Eliminar este movimiento", color = MaterialTheme.colorScheme.error)
                }
                if (edicion.serie > 1) {
                    TextButton(onClick = { onEliminar(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Eliminar toda la serie (${edicion.serie})", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TipoBoton(texto: String, seleccionado: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val fondo = if (seleccionado) color else Color.Transparent
    val contenido = if (seleccionado) Color.White else color
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = fondo,
            contentColor = contenido
        )
    ) {
        Text(texto, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Metodo(texto: String, icono: Painter, onClick: () -> Unit, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(icono, contentDescription = texto, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(texto, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
