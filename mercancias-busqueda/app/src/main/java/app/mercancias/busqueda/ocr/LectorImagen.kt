package app.mercancias.busqueda.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lee una foto o un PDF de la hoja de ruta con el OCR del propio móvil (sin conexión). */
object LectorImagen {
    private const val LADO_MAXIMO = 3200

    suspend fun leer(contexto: Context, uri: Uri): List<FilaLeida> = withContext(Dispatchers.Default) {
        val tipo = contexto.contentResolver.getType(uri).orEmpty()
        val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            if (tipo == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")) {
                leerPdf(contexto, uri, reconocedor)
            } else {
                val imagen = cargarImagen(contexto, uri) ?: error("No se pudo abrir la imagen")
                try { leerPagina(reconocedor, imagen) } finally { imagen.recycle() }
            }
        } finally {
            reconocedor.close()
        }
    }

    /** La hoja puede venir girada: se prueban los cuatro giros y se queda el que más filas da. */
    private suspend fun leerPagina(reconocedor: TextRecognizer, imagen: Bitmap): List<FilaLeida> {
        var mejor = emptyList<FilaLeida>()
        for (giro in listOf(0, 90, 270, 180)) {
            val texto = reconocedor.process(InputImage.fromBitmap(imagen, giro)).esperar()
            val filas = LectorHoja.leer(texto.aPalabras())
            if (filas.size > mejor.size) mejor = filas
            if (mejor.size >= 3) break
        }
        return mejor
    }

    private fun Text.aPalabras(): List<Palabra> =
        textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e ->
            val c = e.boundingBox ?: return@mapNotNull null
            Palabra(e.text, c.left.toFloat(), c.top.toFloat(), c.width().toFloat(), c.height().toFloat())
        }

    private fun cargarImagen(contexto: Context, uri: Uri): Bitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
        var muestra = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / muestra > LADO_MAXIMO * 2) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        val bruta = contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: return null
        val lado = maxOf(bruta.width, bruta.height)
        if (lado <= LADO_MAXIMO) return bruta
        val f = LADO_MAXIMO.toFloat() / lado
        val reducida = Bitmap.createScaledBitmap(bruta, (bruta.width * f).toInt(), (bruta.height * f).toInt(), true)
        if (reducida !== bruta) bruta.recycle()
        return reducida
    }

    private suspend fun leerPdf(contexto: Context, uri: Uri, reconocedor: TextRecognizer): List<FilaLeida> {
        // PdfRenderer necesita un archivo local: se copia a la caché.
        val copia = File(contexto.cacheDir, "hoja.pdf")
        contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            copia.outputStream().use { entrada.copyTo(it) }
        } ?: error("No se pudo abrir el PDF")
        val filas = mutableListOf<FilaLeida>()
        try {
            ParcelFileDescriptor.open(copia, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { pdf ->
                    for (i in 0 until pdf.pageCount) {
                        val imagen = pdf.openPage(i).use { pagina ->
                            val f = LADO_MAXIMO.toFloat() / maxOf(pagina.width, pagina.height)
                            val bmp = Bitmap.createBitmap(
                                (pagina.width * f).toInt(), (pagina.height * f).toInt(), Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            pagina.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            bmp
                        }
                        try { filas += leerPagina(reconocedor, imagen) } finally { imagen.recycle() }
                    }
                }
            }
        } finally {
            copia.delete()
        }
        return filas
    }

    private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }
}
