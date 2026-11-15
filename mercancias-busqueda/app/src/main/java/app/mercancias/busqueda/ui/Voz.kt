package app.mercancias.busqueda.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Devuelve una función que abre el dictado por voz del móvil (en español) mostrando el
 * aviso indicado, y entrega el texto reconocido en [alReconocer].
 */
@Composable
fun rememberDictado(alReconocer: (String) -> Unit): (String) -> Unit {
    val contexto = LocalContext.current
    val entrega by rememberUpdatedState(alReconocer)
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val texto = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !texto.isNullOrBlank()) entrega(texto)
    }
    return { aviso ->
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, aviso)
        }
        try {
            lanzador.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(contexto, "Este móvil no tiene dictado por voz instalado", Toast.LENGTH_LONG).show()
        }
    }
}
