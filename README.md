# Marco Audio Pro

Cliente personal y audiófilo para Navidrome, en **Android** y **Windows** (el mismo código para las dos).

## Instalar

### Android
1. Pasa `MarcoAudioPro.apk` al teléfono (cable, Drive, Telegram…) y ábrelo.
   Android te va a pedir permiso para "instalar apps de origen desconocido".
2. Pon la URL de tu Navidrome, tu usuario y tu contraseña.
   - Para escucharlo fuera de casa, el servidor tiene que ser accesible desde internet:
     un dominio con HTTPS, un reverse proxy o una VPN tipo Tailscale (la más fácil).

### Windows
1. Descomprime `MarcoAudioPro-Windows.zip` donde quieras (por ejemplo en `C:\Programas`).
2. Abre `MarcoAudioPro\MarcoAudioPro.exe`. No necesita instalar Java ni ffmpeg: ya vienen dentro.
3. **Miniplayer estilo iPod**: botón ⧉ en la barra de reproducción. Ventanita flotante siempre
   encima con click wheel: gira la rueda para el volumen; MENU, doble clic en la pantalla o la ✕
   regresan a la app completa; arrastra la pantalla para moverlo.
4. Atajos: **Ctrl+Espacio** play/pausa · **Ctrl+→ / Ctrl+←** siguiente/anterior ·
   **Ctrl+↑ / Ctrl+↓** volumen · también las teclas multimedia del teclado.

Opcional (Ajustes → Reseñas de álbum): API key de Last.fm (gratis) para reseñas en español,
y API key de Anthropic para que Claude escriba la reseña cuando Last.fm no tiene nada.

## Qué trae

- **Diseño estilo Apple Music**: fondo "vivo" con los colores de la portada en movimiento,
  paneles de vidrio, controles blancos, barras finas que se engrosan al tocarlas, letras
  sincronizadas con la línea actual grande y las demás difuminadas, animaciones con resorte.
- **Funciones tipo Spotify**: "Iniciar radio" desde cualquier canción (canciones parecidas) y
  "Populares" del artista.
- **EQ paramétrico de 10 bandas**: peaking, shelves, low/high pass. Procesa en double,
  con pre-amp automático anti-clipping, dither TPDF, presets propios y A/B (mantener presionado).
  La curva se edita arrastrando los nodos, con el espectro en vivo detrás.
- **Visualizadores** que leen el audio real después del EQ: espectro LED, espectrograma,
  VU analógicos (balística VU auténtica, 0 VU = -18 dBFS), goniómetro con medidor de
  correlación y osciloscopio.
- **Ruta de señal** (estilo Roon): fuente → transporte → decodificador → ReplayGain → EQ → salida.
- **ReplayGain** por pista/álbum, sin pasarse nunca del pico real.
- **Me gusta** sincronizado con Navidrome, "Radio Me gusta", letras sincronizadas, scrobbling.
- Reseña del álbum: Last.fm → notas de Navidrome → Claude. Se guarda una vez por álbum.
- **Temas de color**: Ámbar VU, Cian estudio, Rosa neón, Fósforo, Violeta o "Portada".
- Sólo Android: **descargas offline**, caché de 3 GB, calidad separada Wi-Fi/datos, controles
  en notificación, pantalla de bloqueo y audífonos.
- Sólo Windows: motor propio con ffmpeg (FLAC, ALAC, Opus, MP3, AAC, DSD…), salida de 24 bits
  si tu tarjeta la acepta, **gapless** real (precarga la siguiente pista) y visualizadores
  sincronizados con la posición exacta de la salida.

## Estructura

| Módulo | Qué tiene |
|---|---|
| `shared/` | Todo lo común: API de Navidrome, EQ, visualizadores, tema y **todas las pantallas** (Compose Multiplatform). El código vive en `src/jvmShared` y se compila para Android y para PC. |
| `app/` | Lo propio de Android: Media3/ExoPlayer, servicio de reproducción, descargas. |
| `desktop/` | Lo propio de Windows: motor de audio (JavaCV/ffmpeg + Java Sound) y la ventana. |

## Compilar

Requiere JDK 17 y el Android SDK (o Android Studio).

```
gradlew :app:assembleRelease           # APK → <build>/app/outputs/apk/release/
gradlew :desktop:run                   # abre la app de PC
gradlew :desktop:createDistributable   # carpeta con MarcoAudioPro.exe
gradlew :shared:desktopTest :app:testDebugUnitTest   # pruebas del DSP
```

### Pruebas sin tocar el servidor real

`tools/fake-navidrome` es un Navidrome falso (JSON fijos + una pista WAV 24/96 de prueba):

```
python tools/fake-navidrome/build.py   # genera los datos (necesita Pillow)
python tools/fake-navidrome/serve.py   # http://127.0.0.1:8765, cualquier usuario/contraseña
```

- `MARCO_TEST_SERVER=http://127.0.0.1:8765 gradlew :desktop:test` prueba el motor de audio
  de PC (reproducir, seek, gapless, pausa) con volumen 0.
- Con `MARCO_SHOTS_DIR=<carpeta>` además guarda capturas de todas las pantallas (PC, celular
  y el iPod) renderizadas fuera de pantalla. Usa el perfil `dev` (ver abajo).
- `gradlew :desktop:run -Pmarco.profile=dev` usa ajustes separados de los reales;
  `-Pmarco.startMini=true` arranca directo en el miniplayer.

Si el proyecto está en un disco de red (NAS) y Gradle falla con "Permission denied" al
escribir `.class`, agrega a `local.properties` una carpeta local para compilar:

```
marco.buildRoot=C\:/Users/<tu-usuario>/AppData/Local/MarcoAudioProBuild
```

Ojo: el APK se firma con la llave debug de esta PC (`~/.android/debug.keystore`).
Para poder actualizar la app sin desinstalarla, compila siempre desde la misma PC
(o respalda ese archivo).
