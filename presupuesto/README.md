# PRESUPUESTO

App Android para llevar los ingresos y gastos del mes, con saldo corrido.

## Qué hace

- **Campos por movimiento:** fecha, concepto, importe, tipo (ingreso o gasto) y saldo (calculado: lo que queda después de ese movimiento).
- **Resumen:** saldo de hoy (lo que ya ha ocurrido), saldo previsto (incluye lo programado) e ingresos y gastos del mes.
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

## Compilar

Requiere JDK 17 y el SDK de Android (plataforma 35).

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleDebug
```

Para una versión firmada, pon `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) junto a `build.gradle.kts` y ejecuta `./gradlew assembleRelease`. Ese fichero no se sube al repositorio. Usa siempre la misma clave: si cambia, Android no deja instalar la actualización encima de la anterior.
