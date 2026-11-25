package app.presupuesto.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.presupuesto.EdicionTraspaso
import app.presupuesto.logica.parseImporte
import app.presupuesto.model.Cuenta
import app.presupuesto.model.nombre
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FormatoFecha: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")

/** Pasar dinero de una cuenta a otra: sacar del cajero, ingresar efectivo, pagar la tarjeta desde el banco… */
@Composable
fun FormularioTraspaso(
    edicion: EdicionTraspaso,
    cuentas: List<Cuenta>,
    onGuardar: (origen: String, destino: String, importe: String, fecha: LocalDate, concepto: String) -> String?,
    onEliminar: () -> Unit
) {
    key(edicion.sesion) {
        val nuevo = edicion.traspasoId == null
        var origen by remember { mutableStateOf(edicion.origenId) }
        var destino by remember { mutableStateOf(edicion.destinoId) }
        var importe by remember { mutableStateOf(edicion.importe) }
        var concepto by remember { mutableStateOf(edicion.concepto) }
        var fechaTexto by remember { mutableStateOf(edicion.fecha.format(FormatoFecha)) }
        var error by remember { mutableStateOf<String?>(null) }

        val fecha = runCatching { LocalDate.parse(fechaTexto.trim(), FormatoFecha) }.getOrNull()
        val cent = parseImporte(importe)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                if (nuevo) "Nuevo traspaso" else "Editar traspaso",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Mueve dinero entre tus cuentas. No cuenta como ingreso ni como gasto y el total general no cambia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))
            Text("Sale de", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            SelectorCuenta(cuentas, origen) { origen = it }

            Spacer(Modifier.height(14.dp))
            Text("Entra en", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            SelectorCuenta(cuentas, destino) { destino = it }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = importe,
                onValueChange = { importe = it },
                label = { Text("Importe") },
                suffix = { Text("€") },
                singleLine = true,
                isError = importe.isNotBlank() && cent == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                AssistChip(onClick = { fechaTexto = LocalDate.now().format(FormatoFecha) }, label = { Text("Hoy") })
                AssistChip(onClick = { fechaTexto = LocalDate.now().minusDays(1).format(FormatoFecha) }, label = { Text("Ayer") })
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = concepto,
                onValueChange = { concepto = it },
                label = { Text("Concepto (opcional)") },
                placeholder = { Text("Traspaso") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    error = when {
                        fecha == null -> "Revisa la fecha (dd/mm/aaaa)"
                        else -> onGuardar(origen, destino, importe, fecha, concepto)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Guardar traspaso", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (!nuevo) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onEliminar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Eliminar traspaso", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SelectorCuenta(cuentas: List<Cuenta>, elegida: String, onElegir: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        cuentas.forEach { c ->
            FilterChip(
                selected = c.id == elegida,
                onClick = { onElegir(c.id) },
                label = { Text("${c.nombre} · ${c.tipo.nombre().removeSuffix("s")}") }
            )
        }
    }
}
