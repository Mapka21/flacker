# Marco Audio Pro

Cliente Android personal para Navidrome, con enfoque audiófilo.

## Instalar

1. Pasa `MarcoAudioPro.apk` al teléfono (cable, Drive, Telegram…) y ábrelo.
   Android te va a pedir permiso para "instalar apps de origen desconocido".
2. Abre la app y pon la URL de tu Navidrome, tu usuario y tu contraseña.
   - Para escucharlo fuera de casa, el servidor tiene que ser accesible desde internet:
     un dominio con HTTPS, un reverse proxy o una VPN tipo Tailscale (la más fácil).
3. Opcional (Ajustes → Reseñas de álbum):
   - API key de Last.fm (gratis): reseñas en español, tags y estadísticas.
   - API key de Anthropic: si Last.fm no tiene nada del álbum, Claude escribe la reseña.

## Qué trae

- **EQ paramétrico de 10 bandas**: peaking, shelves, low/high pass. Procesa en double,
  con pre-amp automático anti-clipping, dither TPDF, presets propios y A/B (mantener presionado).
  La curva se edita arrastrando los nodos, con el espectro en vivo detrás.
- **Visualizadores** que leen el audio real después del EQ: espectro LED, espectrograma,
  VU analógicos (balística VU auténtica, 0 VU = -18 dBFS), goniómetro con medidor de
  correlación y osciloscopio. Si con Bluetooth se ven adelantados, ajústalo en Ajustes → Sync.
- **Ruta de señal** (estilo Roon): fuente → transporte → decodificador → ReplayGain → EQ → salida.
- **ReplayGain** por pista/álbum, sin pasarse nunca del pico real.
- Calidad de streaming separada para Wi-Fi y datos (original lossless u Opus/MP3 transcodificado).
- Caché de 3 GB: lo que ya escuchaste no vuelve a gastar datos.
- Reseña del álbum: Last.fm → notas de Navidrome → Claude. Se guarda una vez por álbum.
- Letras sincronizadas (si Navidrome las tiene), scrobbling y favoritos sincronizados con el servidor.
- Controles en la notificación, la pantalla de bloqueo y los audífonos Bluetooth.

## Compilar

Requiere JDK 17 y el Android SDK (o Android Studio: abre esta carpeta y dale Run).

```
gradlew assembleRelease      # → app/build/outputs/apk/release/app-release.apk
gradlew testDebugUnitTest    # pruebas del DSP
```

Si en el disco `S:` Gradle falla con "Permission denied" al escribir `.class`,
borra `app/build/tmp/kotlin-classes` y vuelve a compilar.
