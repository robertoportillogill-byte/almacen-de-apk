package app.presupuesto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presupuesto.logica.Presupuesto
import app.presupuesto.model.Datos
import app.presupuesto.model.Movimiento
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FormatoCorto: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
private val FormatoLargo: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
private val FormatoDiaMes: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))

private fun leerFecha(texto: String): LocalDate? =
    runCatching { LocalDate.parse(texto.trim(), FormatoCorto) }.getOrNull()

@Composable
fun PantallaPrevision(
    datos: Datos,
    relleno: PaddingValues,
    onMover: (Set<String>, LocalDate) -> Unit
) {
    val hoy = remember { LocalDate.now() }
    var fechaTexto by remember { mutableStateOf(YearMonth.from(hoy).atEndOfMonth().format(FormatoCorto)) }
    val fecha = leerFecha(fechaTexto)

    // Al cambiar la fecha objetivo (o los datos), se reinician el destino y la selección.
    var destinoTexto by remember(fecha) { mutableStateOf(fecha?.plusDays(1)?.format(FormatoCorto).orEmpty()) }
    val destino = leerFecha(destinoTexto)
    var seleccion by remember(fecha, datos) { mutableStateOf(emptySet<String>()) }

    val prev = remember(datos, fecha) {
        fecha?.let { Presupuesto.prevision(datos.cuentas, datos.movimientos, hoy, it) }
    }
    val simulada = remember(datos, fecha, seleccion, destino) {
        if (fecha == null || destino == null || seleccion.isEmpty()) null
        else Presupuesto.prevision(
            datos.cuentas, Presupuesto.aplazar(datos.movimientos, seleccion, destino), hoy, fecha
        )
    }
    val nombreCuenta = remember(datos) { datos.cuentas.associate { it.id to it.nombre } }

    LazyColumn(
        contentPadding = rellenoLista(relleno),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text("¿Con cuánto contaré?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = fechaTexto,
                onValueChange = { fechaTexto = it },
                label = { Text("A fecha (dd/mm/aaaa)") },
                singleLine = true,
                isError = fecha == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                AssistChip(
                    onClick = { fechaTexto = YearMonth.from(hoy).atEndOfMonth().format(FormatoCorto) },
                    label = { Text("Fin de mes") }
                )
                AssistChip(
                    onClick = { fechaTexto = YearMonth.from(hoy).plusMonths(1).atEndOfMonth().format(FormatoCorto) },
                    label = { Text("Fin del mes que viene") }
                )
                AssistChip(onClick = { fechaTexto = hoy.plusMonths(3).format(FormatoCorto) }, label = { Text("+3 meses") })
                AssistChip(onClick = { fechaTexto = hoy.plusMonths(6).format(FormatoCorto) }, label = { Text("+6 meses") })
            }
        }

        if (prev == null) {
            item { Text("Escribe una fecha válida.", color = MaterialTheme.colorScheme.error) }
            return@LazyColumn
        }

        item { TarjetaResultado(prev.fecha, prev.saldo, prev.minimo, prev.fechaMinimo, Presupuesto.totalesPorTipo(prev.porCuenta)) }

        item {
            Text("Por cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
        items(prev.porCuenta, key = { "c-" + it.cuenta.id }) { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.cuenta.nombre, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(moneda(s.saldo), fontWeight = FontWeight.SemiBold, color = colorSaldo(s.saldo))
            }
        }

        if (prev.minimo >= 0) {
            item {
                Text(
                    "No te quedas en negativo en ningún momento hasta esa fecha.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorIngreso(),
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
            return@LazyColumn
        }

        // ---- Faltan fondos: proponer mover gastos ----
        item {
            Card(
                shape = FormaTarjeta,
                colors = CardDefaults.cardColors(containerColor = colorEgreso().copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Te faltan ${moneda(-prev.minimo)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorEgreso()
                    )
                    Text(
                        "El peor momento es el ${prev.fechaMinimo.format(FormatoLargo)}. " +
                            "Marca los gastos que puedes pagar más tarde y elige a qué fecha moverlos.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = destinoTexto,
                        onValueChange = { destinoTexto = it },
                        label = { Text("Moverlos al (dd/mm/aaaa)") },
                        singleLine = true,
                        isError = destino == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        AssistChip(
                            onClick = { destinoTexto = prev.fecha.plusDays(1).format(FormatoCorto) },
                            label = { Text("Día siguiente") }
                        )
                        AssistChip(
                            onClick = { destinoTexto = prev.fecha.plusMonths(1).withDayOfMonth(1).format(FormatoCorto) },
                            label = { Text("Inicio del mes siguiente") }
                        )
                    }
                    if (destino != null && !destino.isAfter(prev.fecha)) {
                        Text(
                            "Elige una fecha posterior al ${prev.fecha.format(FormatoCorto)}; si no, el problema sigue en la misma fecha.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorEgreso(),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (fecha != null && destino != null) {
                                seleccion = Presupuesto.sugerirAplazar(
                                    datos.cuentas, datos.movimientos, hoy, fecha, destino
                                )
                            }
                        },
                        enabled = destino != null && destino.isAfter(prev.fecha) && prev.gastosAplazables.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Sugerir qué mover")
                    }
                }
            }
        }

        if (prev.gastosAplazables.isEmpty()) {
            item {
                Text(
                    "No hay gastos futuros que mover antes de esa fecha. Habría que añadir un ingreso o revisar los saldos iniciales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            return@LazyColumn
        }

        item {
            Text("Gastos pendientes hasta esa fecha", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
        items(prev.gastosAplazables, key = { "g-" + it.id }) { m ->
            FilaAplazable(m, nombreCuenta[m.cuentaId].orEmpty(), m.id in seleccion) { marcado ->
                seleccion = if (marcado) seleccion + m.id else seleccion - m.id
            }
        }

        item {
            Column(Modifier.padding(top = 8.dp)) {
                simulada?.let { s ->
                    val ok = s.minimo >= 0
                    Text(
                        if (ok) "Con estos cambios tendrías ${moneda(s.saldo)} el ${s.fecha.format(FormatoCorto)} y no te quedas en negativo."
                        else "Con estos cambios tendrías ${moneda(s.saldo)} el ${s.fecha.format(FormatoCorto)}, pero aún faltan ${moneda(-s.minimo)} el ${s.fechaMinimo.format(FormatoCorto)}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ok) colorIngreso() else colorEgreso()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = { if (destino != null) onMover(seleccion, destino) },
                    enabled = seleccion.isNotEmpty() && destino != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        when (seleccion.size) {
                            0 -> "Marca algún gasto para moverlo"
                            1 -> "Mover 1 gasto al ${destino?.format(FormatoCorto).orEmpty()}"
                            else -> "Mover ${seleccion.size} gastos al ${destino?.format(FormatoCorto).orEmpty()}"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaResultado(
    fecha: LocalDate,
    saldo: Long,
    minimo: Long,
    fechaMinimo: LocalDate,
    porTipo: Map<app.presupuesto.model.TipoCuenta, Long>
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(FormaTarjeta)
            .background(fondoCabecera())
            .padding(22.dp)
    ) {
        Text(
            "El ${fecha.format(FormatoLargo)} tendrás",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f)
        )
        Text(
            moneda(saldo),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = if (saldo < 0) Color(0xFFFFC2CC) else Color.White
        )
        if (minimo < saldo) {
            Text(
                "Punto más bajo: ${moneda(minimo)} el ${fechaMinimo.format(FormatoLargo)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (minimo < 0) Color(0xFFFFC2CC) else Color.White.copy(alpha = 0.85f)
            )
        }
        Spacer(Modifier.height(14.dp))
        TotalesPorTipo(porTipo)
    }
}

@Composable
private fun FilaAplazable(m: Movimiento, cuenta: String, marcado: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onCambio(!marcado) }
            .padding(end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = marcado, onCheckedChange = onCambio)
        Column(Modifier.weight(1f)) {
            Text(m.concepto, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extra = if (m.total > 1) " · ${m.indice}/${m.total}" else ""
            Text(
                "${m.fechaLocal.format(FormatoDiaMes)} · $cuenta$extra",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("−" + moneda(m.importeCent), fontWeight = FontWeight.Bold, color = colorEgreso())
    }
}
