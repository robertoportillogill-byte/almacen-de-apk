package app.mercancias.busqueda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mercancias.busqueda.R
import app.mercancias.busqueda.model.Mercancia
import app.mercancias.busqueda.model.formatoKilos
import app.mercancias.busqueda.model.leerBultos
import app.mercancias.busqueda.model.leerKilos

/** Formulario a pantalla completa para escribir o corregir una mercancía (a mano o dictando cada campo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Formulario(inicial: Mercancia, titulo: String, alGuardar: (Mercancia) -> Unit, alCerrar: () -> Unit) {
    var orden by remember { mutableStateOf(if (inicial.orden > 0) inicial.orden.toString() else "") }
    var numOrden by remember { mutableStateOf(inicial.numOrden) }
    var cliente by remember { mutableStateOf(inicial.cliente) }
    var direccion by remember { mutableStateOf(inicial.direccion) }
    var poblacion by remember { mutableStateOf(inicial.poblacion) }
    var bultos by remember { mutableStateOf(inicial.bultosTexto) }
    var kilos by remember { mutableStateOf(if (inicial.kilos > 0) formatoKilos(inicial.kilos) else "") }
    var ubicacion by remember { mutableStateOf(inicial.ubicacion) }
    var observacion by remember { mutableStateOf(inicial.observacion) }
    var destino by remember { mutableStateOf("") }
    var intentado by remember { mutableStateOf(false) }

    val dictar = rememberDictado { texto ->
        when (destino) {
            "orden" -> orden = texto.filter { it.isDigit() }
            "numOrden" -> numOrden = texto.filter { it.isDigit() }
            "cliente" -> cliente = texto.replaceFirstChar { it.uppercase() }
            "direccion" -> direccion = texto.replaceFirstChar { it.uppercase() }
            "poblacion" -> poblacion = texto.replaceFirstChar { it.uppercase() }
            "bultos" -> bultos = leerBultos(texto)?.let { "${it.first}/${it.second}" } ?: texto
            "kilos" -> kilos = leerKilos(texto)?.let { formatoKilos(it) } ?: texto
            "ubicacion" -> ubicacion = texto.replaceFirstChar { it.uppercase() }
            "observacion" -> observacion = texto.replaceFirstChar { it.uppercase() }
        }
    }
    fun voz(campo: String, aviso: String): () -> Unit = { destino = campo; dictar(aviso) }

    val bultosLeidos = leerBultos(bultos)
    val kilosLeidos = if (kilos.isBlank()) 0.0 else leerKilos(kilos)
    val faltaNombre = cliente.isBlank() && numOrden.isBlank()

    fun guardar() {
        intentado = true
        if (bultosLeidos == null || kilosLeidos == null || faltaNombre) return
        alGuardar(
            inicial.copy(
                orden = orden.toIntOrNull() ?: 0,
                numOrden = numOrden.trim(),
                cliente = cliente.trim(),
                direccion = direccion.trim(),
                poblacion = poblacion.trim(),
                bultosTotal = bultosLeidos.first,
                bultosLlevar = bultosLeidos.second,
                kilos = kilosLeidos,
                ubicacion = ubicacion.trim(),
                observacion = observacion.trim(),
                encontrados = inicial.encontrados.coerceAtMost(bultosLeidos.second),
                revisar = false,
            )
        )
    }

    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(titulo) },
                        navigationIcon = {
                            IconButton(onClick = alCerrar) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                        },
                    )
                },
            ) { margen ->
                Column(
                    Modifier
                        .padding(margen)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampoTexto("Orden", orden, { orden = it.filter(Char::isDigit).take(4) },
                            Modifier.weight(1f), KeyboardType.Number, alDictar = voz("orden", "Di el orden"))
                        CampoTexto("Nº de orden", numOrden, { numOrden = it }, Modifier.weight(2f),
                            KeyboardType.Number, alDictar = voz("numOrden", "Di el número de orden"))
                    }
                    CampoTexto("Cliente", cliente, { cliente = it }, Modifier.fillMaxWidth(),
                        error = intentado && faltaNombre,
                        ayuda = if (intentado && faltaNombre) "Pon al menos el cliente o el nº de orden" else null,
                        alDictar = voz("cliente", "Di el cliente"))
                    CampoTexto("Dirección", direccion, { direccion = it }, Modifier.fillMaxWidth(),
                        alDictar = voz("direccion", "Di la dirección"))
                    CampoTexto("Población", poblacion, { poblacion = it }, Modifier.fillMaxWidth(),
                        alDictar = voz("poblacion", "Di la población"))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampoTexto("Bultos", bultos, { bultos = it }, Modifier.weight(1f), KeyboardType.Phone,
                            error = bultosLeidos == null,
                            ayuda = "2/1 = hay 2, llevar 1",
                            alDictar = voz("bultos", "Di los bultos, por ejemplo: 2 de 1"))
                        CampoTexto("Kilos", kilos, { kilos = it }, Modifier.weight(1f), KeyboardType.Decimal,
                            error = kilosLeidos == null, alDictar = voz("kilos", "Di los kilos"))
                    }
                    CampoTexto("Ubicación", ubicacion, { ubicacion = it }, Modifier.fillMaxWidth(),
                        alDictar = voz("ubicacion", "Di la ubicación"))
                    CampoTexto("Observación", observacion, { observacion = it }, Modifier.fillMaxWidth(),
                        unaLinea = false, alDictar = voz("observacion", "Di la observación"))
                    Button(onClick = ::guardar, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp)) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Composable
fun CampoTexto(
    etiqueta: String,
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    error: Boolean = false,
    ayuda: String? = null,
    unaLinea: Boolean = true,
    alDictar: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        modifier = modifier,
        singleLine = unaLinea,
        isError = error,
        supportingText = ayuda?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = teclado, capitalization = KeyboardCapitalization.Sentences),
        trailingIcon = alDictar?.let {
            { IconButton(onClick = it) { Icon(painterResource(R.drawable.ic_mic), contentDescription = "Dictar $etiqueta") } }
        },
    )
}
