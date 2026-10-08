# Flacker

*FLAC + hacker.* (Antes "Marco Audio Pro".) Cliente personal y audiófilo para Navidrome, en **Android** y **Windows** (el mismo código para las dos).

## Instalar

### Android
1. Pasa `Flacker.apk` al teléfono (cable, Drive, Telegram…) y ábrelo.
   Android te va a pedir permiso para "instalar apps de origen desconocido". Se instala
   encima de Marco Audio Pro: conserva tu sesión, descargas y EQ.
2. Pon la URL de tu Navidrome, tu usuario y tu contraseña.
   - Para escucharlo fuera de casa, el servidor tiene que ser accesible desde internet:
     un dominio con HTTPS, un reverse proxy o una VPN tipo Tailscale (la más fácil).

### Windows
1. Descomprime `Flacker-Windows.zip` donde quieras (por ejemplo en `C:\Programas`).
2. Abre `Flacker\Flacker.exe`. No necesita instalar Java ni ffmpeg: ya vienen dentro.
3. **Miniplayer iPod nano**: botón ⧉ arriba a la derecha. iPod nano del color de la portada,
   siempre encima; se arrastra desde cualquier parte menos la rueda. Tres tamaños (botón
   S/M/L arriba a la izquierda). Un clic en la pantalla cambia entre la canción y los
   visualizadores (Neón 360 estilo Xbox 360, Hiperespacio, Halo). Gira la rueda para el
   volumen; MENU, doble clic en la pantalla o la ✕ regresan a la app completa.
4. Atajos: **Ctrl+Espacio** play/pausa · **Ctrl+→ / Ctrl+←** siguiente/anterior ·
   **Ctrl+↑ / Ctrl+↓** volumen · **Alt+← / Alt+→** atrás/adelante · teclas multimedia.
5. Mouse: **clic derecho** en canciones y álbumes abre su menú; **rueda** sobre el reproductor,
   la barra de volumen o el iPod cambia el volumen; los **botones laterales** van atrás/adelante.

Opcional (Ajustes → Reseñas de álbum): API key de Last.fm (gratis) para reseñas en español,
y API key de Anthropic para que Claude escriba la reseña cuando Last.fm no tiene nada.

## Qué trae

- **Descubrir** (y arriba en Inicio): artistas, álbumes y canciones que **no tienes**,
  parecidos a lo que más escuchas y a tus favoritos, más "Te faltan de …" (discos de tus
  artistas que no están en tu biblioteca). Catálogo de la API pública de Deezer, se refresca
  cada 12 h. La búsqueda también muestra "Fuera de tu biblioteca".
- **Consíguelo en alta calidad**: cada artista/álbum de fuera tiene botones a tiendas legales
  con FLAC/Hi-Res (Qobuz, Bandcamp, 7digital, Presto) y a Archive.org (FLAC gratis y legal).
  Lo compras, lo pones en la carpeta de Navidrome y aparece en tu biblioteca.
- **Playlists**: crear, renombrar, borrar, agregar canciones o álbumes enteros ("Agregar a
  playlist…" en el menú) y quitar canciones. Todo se guarda en Navidrome.
- **Portadas propias** para playlists, "Me gusta" y artistas (botón ✎ sobre la portada).
  Se guardan en cada dispositivo.
- **Biblioteca → Canciones**: todas tus rolas, cargando por páginas.
- **Una sola barra de búsqueda** (la de arriba en la PC), como en Spotify.
- **Modo visual**: el fondo de la app es el visualizador y baila con la música (bajo, medios,
  agudos y golpes). Presets: Aurora, Pulso, Halo, Olas, Hiperespacio, Synthwave y Neón 360.
  Botón ✨ para encenderlo/apagarlo y elegir preset viéndolos en vivo.
- **Letra en su propio módulo** (como Apple Music): en la PC, el reproductor completo pone la
  letra a la derecha y la barra derecha tiene la pestaña "Letra"; en el celular, el botón de
  letra la pone en grande con la portada chiquita arriba.
- **Connect** (como Spotify Connect, sin nube): el celular y la PC se ven en la misma red
  Wi-Fi. Botón de dispositivos → tocas tu PC y la canción sigue ahí en el mismo segundo;
  "Traer aquí" la regresa. Si algo suena en otro dispositivo aparece la barra verde
  "Reproduciendo en …" con control remoto. Sólo se ven dispositivos con tu misma cuenta de
  Navidrome. En la PC, la primera vez Windows pregunta por el firewall: permite "redes privadas".
- **Letras de internet**: si Navidrome no tiene la letra, se busca en LRCLIB (sincronizada
  cuando existe) y se guarda.
- **PC en tres columnas**: barra lateral (menú, biblioteca, tus playlists), contenido, y
  Reproduciendo + A continuación a la derecha, en paneles de vidrio sobre el fondo vivo.
  En ventanas angostas el reproductor se vuelve una píldora flotante.

- **Diseño estilo Apple Music**: fondo "vivo" con los colores de la portada en movimiento,
  paneles de vidrio, controles blancos, barras finas que se engrosan al tocarlas, letras
  sincronizadas con la línea actual grande y las demás difuminadas, animaciones con resorte.
- **Funciones tipo Spotify**: "Iniciar radio" desde cualquier canción (canciones parecidas) y
  "Populares" del artista.
- **EQ paramétrico de 10 bandas**: peaking, shelves, low/high pass. Procesa en double,
  con pre-amp automático anti-clipping, dither TPDF, presets propios y A/B (mantener presionado).
  La curva se edita arrastrando los nodos, con el espectro en vivo detrás.
- **Medidores de estudio** (en "Señal") que leen el audio real después del EQ: VU analógicos
  (balística VU auténtica, 0 VU = -18 dBFS), espectro LED, espectrograma, goniómetro con
  medidor de correlación y osciloscopio.
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
gradlew :desktop:createDistributable   # carpeta con Flacker.exe
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
