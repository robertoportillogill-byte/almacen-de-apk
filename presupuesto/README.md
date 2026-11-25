# PRESUPUESTO

App Android para llevar los ingresos y gastos del mes, con saldo corrido.

## Qué hace

- **Campos por movimiento:** fecha, concepto, importe, tipo (ingreso o gasto) y saldo (calculado: lo que queda después de ese movimiento).
- **Cuentas (pestaña «Cuentas»):** Caja, Banco y Tarjetas. Dentro de cada tipo se crean las entidades que hagan falta (p. ej. «Efectivo», «Santander», «Visa BBVA»), cada una con su saldo inicial. En una tarjeta de crédito el saldo inicial puede ser negativo (lo que se debe). Cada movimiento va a una cuenta.
- **Resumen:** total general de hoy, subtotal de Caja, Banco y Tarjetas, saldo previsto (incluye lo programado) e ingresos y gastos del mes.
- **Previsión (pestaña «Previsión»):** con cuánto dinero contarás en una fecha, en total y por cuenta, y cuál es el punto más bajo hasta llegar a ella. Si en algún momento queda en negativo, lista los gastos pendientes hasta esa fecha para marcar cuáles mover a otra fecha; el botón «Sugerir qué mover» elige los menos gastos posibles que cubren lo que falta.
- **Repetir:**
  - *Una vez*.
  - *Mensual*: para ingresos o gastos. Se crea una fila por mes durante el número de meses que indiques (12 por defecto).
  - *En cuotas* (solo gastos): el importe total se reparte en N cuotas, una al mes. Si no divide exacto, las primeras cuotas suman un céntimo más para que el total cuadre.
- **Cuatro formas de cargar datos:**
  - Escribir a mano.
  - Dictar por voz («gasto 600 euros televisor en 6 cuotas», «ingreso 1.500 nómina mensual durante 3 meses», «gasto 20 luz el 3 de marzo»).
  - Escanear un documento con la cámara (ticket o factura): la app lee la fecha, el concepto y el total.
  - Elegir una foto de la galería, con el mismo lector.
- **Editar o borrar** un movimiento. En una serie se puede borrar solo la fila o toda la serie.
- Los datos se guardan solo en el móvil (`presupuesto.json` en el almacenamiento privado de la app). El reconocimiento de texto funciona sin conexión.

## Limitaciones conocidas

- El escaneo usa la foto de la cámara; no detecta los bordes del papel ni endereza la imagen. Conviene fotografiar el documento plano y con buena luz.
- La voz usa el reconocedor de voz de Android (en español). Tras dictar, revisa el formulario antes de guardar.
- Los movimientos no se sincronizan entre móviles ni se exportan todavía.
- No hay traspasos entre cuentas (p. ej. sacar del cajero o pagar la tarjeta desde el banco). Por ahora se apuntan como un gasto en una cuenta y un ingreso en la otra.
- Los datos de la versión 1.0 pasan automáticamente a una caja llamada «Efectivo», que se puede renombrar.

## Compilar

Requiere JDK 17 y el SDK de Android (plataforma 35).

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleDebug
```

Para una versión firmada, pon `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) junto a `build.gradle.kts` y ejecuta `./gradlew assembleRelease`. Ese fichero no se sube al repositorio. Usa siempre la misma clave: si cambia, Android no deja instalar la actualización encima de la anterior.
