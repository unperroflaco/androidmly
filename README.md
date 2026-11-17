# Mallorca Likes You - Android

Aplicación Android nativa desarrollada en Java. No utiliza WebView ni contenido HTML.

## Primera versión

- Obtiene la ubicación del dispositivo con Google Play Services.
- Si no hay permiso, no se obtiene una ubicación o el dispositivo está fuera del área geográfica de Mallorca, utiliza las coordenadas de referencia **39.6953, 3.0176**.
- Consulta la API de Mallorca Likes You con las categorías iniciales `Playa,Pueblo` y un límite de dos resultados.
- Muestra tarjetas nativas con nombre, categoría, municipio, distancia, descripción y contenido multimedia.
- Admite imágenes y vídeos MP4 en el detalle del lugar.
- Muestra el autor de cada recurso; si falta, utiliza **Paco Gómez 2025**.
- Permite abrir la navegación hacia el lugar en Google Maps.

## API

`https://mallorca.likesyou.org/backend/api/buscar.php`

Parámetros iniciales:
- `lat`: latitud seleccionada
- `lng`: longitud seleccionada
- `idioma=es`
- `categorias=Playa,Pueblo`
- `limite=2`

## Compilación

El workflow **Android APK** se ejecuta al hacer push a `main` o manualmente desde GitHub Actions.

El APK de depuración se publica como artefacto con el nombre **Mallorca-Likes-You-debug**.
