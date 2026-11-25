package app.presupuesto.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Reconocimiento de texto de una foto o escaneo. Funciona sin conexión. */
object LectorDocumento {

    fun leer(
        contexto: Context,
        uri: Uri,
        alTerminar: (texto: String?) -> Unit
    ) {
        val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val imagen = InputImage.fromFilePath(contexto, uri)
            reconocedor.process(imagen)
                .addOnSuccessListener { alTerminar(it.text) }
                .addOnFailureListener { alTerminar(null) }
        } catch (e: Exception) {
            alTerminar(null)
        }
    }
}
