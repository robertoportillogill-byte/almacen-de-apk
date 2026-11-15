package app.mercancias.busqueda.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mercancias.busqueda.model.Mercancia
import app.mercancias.busqueda.model.formatoKilos
import app.mercancias.busqueda.model.leerBultos

/** Pantalla para repasar lo que se ha leído de la hoja antes de añadirlo a la lista. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Revision(
    filas: List<Mercancia>,
    alCambiar: (List<Mercancia>) -> Unit,
    alEditar: (Mercancia) -> Unit,
    alCancelar: () -> Unit,
    alAceptar: () -> Unit,
) {
    val dudosas = filas.count { it.revisar }
    Dialog(onDismissRequest = alCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Revisar ${filas.size} filas leídas") },
                        navigationIcon = {
                            IconButton(onClick = alCancelar) { Icon(Icons.Default.Close, contentDescription = "Cancelar") }
                        },
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 3.dp) {
                        Button(
                            onClick = alAceptar,
                            enabled = filas.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                        ) { Text("Añadir ${filas.size} a la lista") }
                    }
                },
            ) { margen ->
                LazyColumn(
                    Modifier.padding(margen),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            if (dudosas > 0) "Comprueba los bultos: en $dudosas filas (borde naranja) no se han leído bien, " +
                                "normalmente por estar rodeados a boli. Corrígelos aquí mismo; 2/1 = hay 2, llevar 1."
                            else "Comprueba que los bultos coinciden con la hoja. 2/1 = hay 2, llevar 1.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dudosas > 0) Naranja else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(filas, key = { it.id }) { f ->
                        FilaRevision(
                            f = f,
                            alCambiarBultos = { total, llevar ->
                                alCambiar(filas.map {
                                    if (it.id == f.id) it.copy(bultosTotal = total, bultosLlevar = llevar, revisar = false) else it
                                })
                            },
                            alEditar = { alEditar(f) },
                            alQuitar = { alCambiar(filas.filterNot { it.id == f.id }) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaRevision(f: Mercancia, alCambiarBultos: (Int, Int) -> Unit, alEditar: () -> Unit, alQuitar: () -> Unit) {
    var bultos by remember(f.id) { mutableStateOf(f.bultosTexto) }
    OutlinedCard(
        border = BorderStroke(if (f.revisar) 2.dp else 1.dp, if (f.revisar) Naranja else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    listOfNotNull(if (f.orden > 0) "Orden ${f.orden}" else null, "Nº ${f.numOrden}").joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(f.cliente.ifBlank { "(cliente sin leer)" }, fontWeight = FontWeight.SemiBold)
                if (f.direccionCompleta.isNotBlank()) Text(f.direccionCompleta, style = MaterialTheme.typography.bodySmall)
                Text("${formatoKilos(f.kilos)} kg", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = bultos,
                onValueChange = { t ->
                    bultos = t
                    leerBultos(t)?.let { alCambiarBultos(it.first, it.second) }
                },
                label = { Text("Bultos") },
                singleLine = true,
                isError = f.revisar || leerBultos(bultos) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.width(92.dp),
            )
            Column {
                IconButton(onClick = alEditar) { Icon(Icons.Default.Edit, contentDescription = "Corregir fila") }
                IconButton(onClick = alQuitar) { Icon(Icons.Default.Delete, contentDescription = "Quitar fila") }
            }
        }
    }
}
