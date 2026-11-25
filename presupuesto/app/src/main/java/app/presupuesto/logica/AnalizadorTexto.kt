package app.presupuesto.logica

import app.presupuesto.model.Borrador
import app.presupuesto.model.Repeticion
import app.presupuesto.model.Tipo
import app.presupuesto.model.TipoCuenta
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Saca un borrador de texto libre.
 *  - Voz: «gasto 350 euros luz de enero» / «ingreso 1.500 nómina mensual durante 6 meses»
 *         «televisor 600 euros en 6 cuotas» / «cobro 20 el 3 de mayo»
 *  - Documento escaneado: fecha, concepto (primera línea útil) e importe total.
 */
object AnalizadorTexto {

    private val meses = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5, "junio" to 6,
        "julio" to 7, "agosto" to 8, "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12
    )

    private val reCuotas = Regex("""(?:en\s+)?(\d{1,3})\s*(?:cuotas?|plazos?)""", RegexOption.IGNORE_CASE)
    private val reMensual = Regex(
        """(?:mensual(?:mente)?|cada\s+mes|todos\s+los\s+meses|al\s+mes)(?:\s+durante\s+(\d{1,3})\s*meses?)?""",
        RegexOption.IGNORE_CASE
    )
    private val reFechaNumerica = Regex("""\b(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2}|\d{4}))?\b""")
    private val reFechaLarga = Regex(
        """\b(\d{1,2})\s+de\s+([a-záéíóú]+)(?:\s+de\s+(\d{4}))?""", RegexOption.IGNORE_CASE
    )
    private val reMoneda = Regex(
        """(\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s*(?:€|euros?\b|eur\b)""", RegexOption.IGNORE_CASE
    )
    // Primero miles con punto ("1.500"), luego decimales ("12,50" o "12.5").
    private val reNumero = Regex("""\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?""")
    private val reTipoIngreso = Regex(
        """ingres|cobr[eéo]|n[oó]mina|me\s+pag|me\s+han\s+pagado|transferencia\s+recibida|abono""",
        RegexOption.IGNORE_CASE
    )
    private val reRuidoConcepto = Regex(
        """\b(con\s+tarjeta|tarjeta|en\s+efectivo|efectivo|en\s+met[aá]lico|met[aá]lico|por\s+banco|banco|bizum|gastos?|ingres[oéa]r?|cobr[eéo]|pagu[eé]|pago|he\s+pagado|me\s+pagan|compra|por|hoy|ayer|anteayer|euros?|eur|el|la|los|las|de|en|para|un|una)\b|€""",
        RegexOption.IGNORE_CASE
    )

    /** «con tarjeta», «en efectivo», «por banco»… -> tipo de cuenta. */
    fun detectarTipoCuenta(texto: String): TipoCuenta? = when {
        Regex("""tarjeta|visa|mastercard|cr[eé]dito|d[eé]bito""", RegexOption.IGNORE_CASE).containsMatchIn(texto) ->
            TipoCuenta.TARJETA
        Regex("""efectivo|met[aá]lico|\bcaja\b|en\s+mano""", RegexOption.IGNORE_CASE).containsMatchIn(texto) ->
            TipoCuenta.CAJA
        Regex("""\bbanco\b|transferencia|domiciliaci|bizum|recibo""", RegexOption.IGNORE_CASE).containsMatchIn(texto) ->
            TipoCuenta.BANCO
        else -> null
    }

    fun analizarVoz(texto: String, hoy: LocalDate = LocalDate.now()): Borrador {
        var resto = texto.replace(Regex("\\s+"), " ").trim()

        val tipo = if (reTipoIngreso.containsMatchIn(resto)) Tipo.INGRESO else Tipo.EGRESO
        val tipoCuenta = detectarTipoCuenta(resto)

        var repeticion = Repeticion.NINGUNA
        var veces = 12

        reCuotas.find(resto)?.let { m ->
            repeticion = Repeticion.CUOTAS
            veces = m.groupValues[1].toInt().coerceAtLeast(1)
            resto = resto.replaceRange(m.range, " ")
        }
        if (repeticion == Repeticion.NINGUNA) {
            reMensual.find(resto)?.let { m ->
                repeticion = Repeticion.MENSUAL
                veces = m.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()?.coerceAtLeast(1) ?: 12
                resto = resto.replaceRange(m.range, " ")
            }
        }

        var fecha = hoy
        if (Regex("""\banteayer\b""", RegexOption.IGNORE_CASE).containsMatchIn(resto)) fecha = hoy.minusDays(2)
        else if (Regex("""\bayer\b""", RegexOption.IGNORE_CASE).containsMatchIn(resto)) fecha = hoy.minusDays(1)
        fechaDeTexto(resto, hoy)?.let { (f, rango) ->
            fecha = f
            resto = resto.replaceRange(rango, " ")
        }

        // Importe: primero el que lleva «€» o «euros»; si no, el primer número que quede.
        val importe = reMoneda.find(resto)?.let { m ->
            parseImporte(m.groupValues[1])?.let { cent -> cent to m.range }
        } ?: reNumero.findAll(resto).firstNotNullOfOrNull { m ->
            parseImporte(m.value)?.let { cent -> cent to m.range }
        }

        var importeTexto = ""
        importe?.let { (cent, rango) ->
            importeTexto = formatearImporte(cent)
            resto = resto.replaceRange(rango, " ")
        }

        val concepto = limpiarConcepto(resto)

        return Borrador(
            tipo = tipo,
            fecha = fecha,
            concepto = concepto,
            importe = importeTexto,
            repeticion = if (tipo == Tipo.INGRESO && repeticion == Repeticion.CUOTAS) Repeticion.NINGUNA else repeticion,
            veces = veces,
            tipoCuenta = tipoCuenta
        )
    }

    /** Documento escaneado o foto de ticket/factura. */
    fun analizarDocumento(texto: String, hoy: LocalDate = LocalDate.now()): Borrador {
        val lineas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val fecha = fechaDeTexto(texto, hoy)?.first ?: hoy

        val reTotal = Regex("""total|importe|a\s+pagar|total\s+eur""", RegexOption.IGNORE_CASE)
        val importeLinea = lineas.filter { reTotal.containsMatchIn(it) }
            .flatMap { importesEn(it) }.lastOrNull()
        val importe = importeLinea ?: lineas.flatMap { importesEn(it) }.maxOrNull()

        val ruido = Regex(
            """factura|ticket|albar[aá]n|n\.?\s*i\.?\s*f|cif|nif|tel|www|@|fecha|total|iva|cambio|efectivo|tarjeta|visa|mastercard|gracias""",
            RegexOption.IGNORE_CASE
        )
        val concepto = lineas.firstOrNull { linea ->
            linea.count { it.isLetter() } >= 3 && !ruido.containsMatchIn(linea) &&
                reFechaNumerica.find(linea) == null && importesEn(linea).isEmpty()
        }?.take(60).orEmpty()

        val esIngreso = Regex("""ingreso|transferencia\s+recibida|abono|n[oó]mina""", RegexOption.IGNORE_CASE)
            .containsMatchIn(texto)

        return Borrador(
            tipo = if (esIngreso) Tipo.INGRESO else Tipo.EGRESO,
            fecha = fecha,
            concepto = concepto,
            importe = importe?.let { formatearImporte(it) }.orEmpty(),
            tipoCuenta = detectarTipoCuenta(texto)
        )
    }

    /** Devuelve la fecha encontrada y el rango del texto que la ocupa. */
    private fun fechaDeTexto(texto: String, hoy: LocalDate): Pair<LocalDate, IntRange>? {
        reFechaNumerica.find(texto)?.let { m ->
            val dia = m.groupValues[1].toInt()
            val mes = m.groupValues[2].toInt()
            val anyoRaw = m.groupValues[3]
            val anyo = when {
                anyoRaw.isEmpty() -> hoy.year
                anyoRaw.length == 2 -> 2000 + anyoRaw.toInt()
                else -> anyoRaw.toInt()
            }
            return try { LocalDate.of(anyo, mes, dia) to m.range } catch (_: DateTimeException) { null }
        }
        reFechaLarga.find(texto)?.let { m ->
            val mes = meses[m.groupValues[2].lowercase()] ?: return null
            val anyo = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: hoy.year
            return try { LocalDate.of(anyo, mes, m.groupValues[1].toInt()) to m.range } catch (_: DateTimeException) { null }
        }
        return null
    }

    private fun importesEn(linea: String): List<Long> {
        val re = Regex("""\d{1,3}(?:\.\d{3})*,\d{2}\b|\d+\.\d{2}\b|\d+,\d{2}\b""")
        return re.findAll(linea).mapNotNull { parseImporte(it.value) }.toList()
    }

    private fun limpiarConcepto(texto: String): String {
        val limpio = texto.replace(reRuidoConcepto, " ")
            .replace(Regex("""^\s*(de|en|para|el|la|los|las|un|una)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', ';', ':', '-')
        if (limpio.isEmpty()) return ""
        return limpio.replaceFirstChar { it.uppercase() }.take(60)
    }
}
