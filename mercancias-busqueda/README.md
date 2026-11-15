# Mercancías Búsqueda

App Android para localizar la mercancía de una hoja de ruta de reparto.

## Qué hace

- **Campos por mercancía:** orden, nº de orden, cliente, dirección y población, bultos, kilos, encontrado,
  ubicación y observación.
- **Bultos `total/a llevar`:** `1/1` es un bulto; `2/1` significa que hay 2 pero solo hay que llevar 1.
  Lo que se busca es la segunda cifra.
- **Encontrados:** al encontrar todos los bultos la mercancía pasa a la pestaña «Encontrados».
  Si de 4 solo aparecen 2, se queda en «Pendientes» con el aviso «Faltan 2 de 4 bultos».
- **Ordenar y buscar** por cualquier campo, en cualquier momento.
- **Cuatro formas de meter datos:** escanear la hoja con la cámara, elegir una foto o PDF, escribir a mano
  o dictar por voz («cliente …, dirección …, bultos 2 de 1, kilos …»).
- Los datos se guardan solo en el móvil. El reconocimiento de texto funciona sin conexión.

## Lectura de la hoja

La foto se pasa por el OCR del móvil (ML Kit) y `LectorHoja` reconstruye la tabla por posición: las
cabeceras marcan las columnas y el nº de orden marca cada fila. Las anotaciones a boli se descartan por
tamaño. Antes de añadir nada se muestra una pantalla de revisión; los bultos que no se han podido leer
(por ejemplo, rodeados a boli) salen marcados en naranja para corregirlos.

## Compilar

Requiere JDK 17 o superior y el SDK de Android (plataforma 35).

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleRelease
```

La firma se lee de `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), que
no se sube al repositorio. Hay que usar siempre la misma clave: si cambia, Android no deja actualizar la
app encima de la anterior.
