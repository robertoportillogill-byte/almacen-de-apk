package app.mercancias.busqueda.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mercancias.busqueda.model.Mercancia
import app.mercancias.busqueda.model.formatoKilos

/** Hoja inferior para marcar cuántos bultos se han encontrado, dónde están y anotar observaciones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detalle(
    m: Mercancia,
    ubicacionesUsadas: List<String>,
    alGuardar: (Mercancia) -> Unit,
    alEditar: () -> Unit,
    alEliminar: () -> Unit,
    alCerrar: () -> Unit,
) {
    var encontrados by remember(m.id) { mutableIntStateOf(m.encontrados) }
    var ubicacion by remember(m.id) { mutableStateOf(m.ubicacion) }
    var observacion by remember(m.id) { mutableStateOf(m.observacion) }
    var destino by remember { mutableStateOf("") }
    val dictar = rememberDictado { t ->
        val texto = t.replaceFirstChar { it.uppercase() }
        if (destino == "ubicacion") ubicacion = texto else observacion = texto
    }
    val faltan = (m.bultosLlevar - encontrados).coerceAtLeast(0)

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(m.cliente.ifBlank { "(sin cliente)" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(
                    if (m.orden > 0) "Orden ${m.orden}" else null,
                    m.numOrden.takeIf { it.isNotBlank() }?.let { "Nº $it" },
                    m.direccionCompleta.takeIf { it.isNotBlank() },
                    "${formatoKilos(m.kilos)} kg",
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (m.bultosTotal == m.bultosLlevar) "Bultos a buscar: ${m.bultosLlevar}"
                else "Bultos ${m.bultosTexto}: hay ${m.bultosTotal}, solo hay que llevar ${m.bultosLlevar}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )

            Text("Encontrados", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { if (encontrados > 0) encontrados-- }, enabled = encontrados > 0) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "$encontrados de ${m.bultosLlevar}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                FilledTonalButton(onClick = { if (encontrados < m.bultosLlevar) encontrados++ }, enabled = encontrados < m.bultosLlevar) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { encontrados = m.bultosLlevar }) { Text("Todos") }
            }
            Text(
                when {
                    faltan == 0 -> "Completo: pasará a «Encontrados»."
                    encontrados == 0 -> "Sin encontrar todavía."
                    else -> "Queda pendiente: faltan $faltan de ${m.bultosLlevar} bultos."
                },
                color = when {
                    faltan == 0 -> Verde
                    encontrados == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> Naranja
                },
                fontWeight = FontWeight.SemiBold,
            )

            CampoTexto("Ubicación", ubicacion, { ubicacion = it }, Modifier.fillMaxWidth(),
                alDictar = { destino = "ubicacion"; dictar("Di la ubicación") })
            if (ubicacionesUsadas.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ubicacionesUsadas.forEach { u -> SuggestionChip(onClick = { ubicacion = u }, label = { Text(u) }) }
                }
            }
            CampoTexto("Observación", observacion, { observacion = it }, Modifier.fillMaxWidth(), unaLinea = false,
                alDictar = { destino = "observacion"; dictar("Di la observación") })

            Button(
                onClick = { alGuardar(m.copy(encontrados = encontrados, ubicacion = ubicacion.trim(), observacion = observacion.trim())) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = alEditar, modifier = Modifier.weight(1f)) { Text("Editar datos") }
                OutlinedButton(onClick = alEliminar, modifier = Modifier.weight(1f)) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
