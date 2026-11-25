package app.presupuesto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presupuesto.EdicionCuenta
import app.presupuesto.logica.Presupuesto
import app.presupuesto.logica.SaldoCuenta
import app.presupuesto.logica.formatearSaldo
import app.presupuesto.model.Cuenta
import app.presupuesto.model.Datos
import app.presupuesto.model.TipoCuenta
import app.presupuesto.model.nombre
import java.time.LocalDate

@Composable
fun PantallaCuentas(
    datos: Datos,
    relleno: PaddingValues,
    onNueva: (TipoCuenta) -> Unit,
    onEditar: (Cuenta) -> Unit
) {
    val hoy = remember { LocalDate.now() }
    val saldos = remember(datos) { Presupuesto.saldosCuentas(datos.cuentas, datos.movimientos, hoy) }
    val porTipo = remember(saldos) { Presupuesto.totalesPorTipo(saldos) }
    val total = saldos.sumOf { it.saldo }

    LazyColumn(
        contentPadding = rellenoLista(relleno),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(FormaTarjeta)
                    .background(fondoCabecera())
                    .padding(22.dp)
            ) {
                Text("Total general hoy", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Text(moneda(total), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(14.dp))
                TotalesPorTipo(porTipo)
            }
        }

        TipoCuenta.entries.forEach { tipo ->
            val deTipo = saldos.filter { it.cuenta.tipo == tipo }
            item(key = "tipo-$tipo") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tipo.nombre(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            moneda(porTipo[tipo] ?: 0),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorSaldo(porTipo[tipo] ?: 0)
                        )
                    }
                    TextButton(onClick = { onNueva(tipo) }) { Text("+ Añadir") }
                }
            }
            if (deTipo.isEmpty()) {
                item(key = "vacio-$tipo") {
                    Text(
                        when (tipo) {
                            TipoCuenta.CAJA -> "Sin caja. Añade el efectivo que llevas."
                            TipoCuenta.BANCO -> "Sin bancos. Añade cada cuenta bancaria."
                            TipoCuenta.TARJETA -> "Sin tarjetas. Añade cada tarjeta de crédito o débito."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            items(deTipo, key = { it.cuenta.id }) { s -> FilaCuenta(s) { onEditar(s.cuenta) } }
        }
    }
}

@Composable
fun TotalesPorTipo(porTipo: Map<TipoCuenta, Long>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TipoCuenta.entries.forEach { t ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(t.nombre(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                Text(
                    moneda(porTipo[t] ?: 0),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FilaCuenta(s: SaldoCuenta, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = FormaTarjeta,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.cuenta.nombre.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.cuenta.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Saldo inicial ${moneda(s.cuenta.saldoInicialCent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                moneda(s.saldo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorSaldo(s.saldo)
            )
        }
    }
}

@Composable
fun FormularioCuenta(
    edicion: EdicionCuenta,
    onGuardar: (nombre: String, tipo: TipoCuenta, saldo: String) -> String?,
    onEliminar: () -> String?
) {
    key(edicion.sesion) {
        val c = edicion.cuenta
        var nombre by remember { mutableStateOf(c?.nombre.orEmpty()) }
        var tipo by remember { mutableStateOf(edicion.tipoInicial) }
        var saldo by remember { mutableStateOf(c?.let { formatearSaldo(it.saldoInicialCent) }.orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                if (c == null) "Nueva cuenta" else "Editar cuenta",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoCuenta.entries.forEach { t ->
                    FilterChip(
                        selected = tipo == t,
                        onClick = { tipo = t },
                        label = { Text(t.nombre().removeSuffix("s")) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = {
                    Text(
                        when (tipo) {
                            TipoCuenta.CAJA -> "Nombre (p. ej. Efectivo, Hucha)"
                            TipoCuenta.BANCO -> "Entidad (p. ej. Santander, BBVA)"
                            TipoCuenta.TARJETA -> "Tarjeta (p. ej. Visa BBVA)"
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = saldo,
                onValueChange = { saldo = it },
                label = { Text("Saldo inicial") },
                suffix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Text(
                if (tipo == TipoCuenta.TARJETA)
                    "En una tarjeta de crédito, pon lo que debes en negativo (por ejemplo -350,00). Los gastos con la tarjeta aumentan la deuda."
                else
                    "Lo que hay ahora mismo en esta cuenta. Los movimientos que añadas se suman o restan a partir de aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = { error = onGuardar(nombre, tipo, saldo) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Guardar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (c != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { error = onEliminar() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun colorSaldo(cent: Long): Color = if (cent < 0) colorEgreso() else MaterialTheme.colorScheme.onSurface

fun rellenoLista(relleno: PaddingValues) = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = relleno.calculateTopPadding() + 12.dp,
    bottom = relleno.calculateBottomPadding() + 96.dp
)
