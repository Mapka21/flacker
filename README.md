# Flacker

Tu música de Navidrome en alta fidelidad, en **Android** y **Windows** (el mismo código para las dos). (Antes "Marco Audio Pro".)

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
   visualizadores (Neón 360 estilo Xbox 360, Nexo 2029, Hiperespacio, Halo). Gira la rueda para el
   volumen; MENU, doble clic en la pantalla o la ✕ regresan a la app completa.
4. Atajos (como Spotify): **Espacio** play/pausa (menos mientras escribes en un campo) ·
   **Ctrl+→ / Ctrl+←** siguiente/anterior · **Ctrl+↑ / Ctrl+↓** volumen · **Alt+← / Alt+→**
   atrás/adelante · teclas multimedia (funcionan aunque la app no esté al frente).
   - **Barra de tareas**: al pasar el mouse por el ícono, la vista previa trae ⏮ ⏯ ⏭.
   - **Control multimedia de Windows**: lo que suena aparece con portada y barra de avance en
     el panel de volumen / Configuración rápida y en la pantalla de bloqueo.
5. Mouse: **clic derecho** en canciones y álbumes abre su menú; **rueda** sobre el reproductor,
   la barra de volumen o el iPod cambia el volumen; los **botones laterales** van atrás/adelante.

Opcional (Ajustes → Reseñas de álbum): API key de Last.fm (gratis) para reseñas en español,
y API key de Anthropic para que Claude escriba la reseña cuando Last.fm no tiene nada.

## Qué trae

- **v2.7**:
  - **PC sin la barra de título de Windows** (como Discord o Claude): la app dibuja sus botones de
    minimizar, maximizar y cerrar; se arrastra desde arriba, la barra de búsqueda o el logo, con
    doble clic para maximizar y acoplar a los lados, y cambia de tamaño desde cualquier orilla
    (también la de arriba). `-Dflacker.nativeTitle=true` regresa la barra de Windows.
  - **Ícono en arte ASCII** (v2.7.1): la calavera hecha de caracteres en todos los tamaños de ícono
    (barra de tareas, escritorio, ventana, Android), con cuadrículas más gruesas en los chicos para que
    se lea (`tools/brand/make_icons.py`). Dentro de la app la barra lateral lleva la calavera sólida; el
    arte ASCII queda en el inicio de sesión, Ajustes y "sin letra".
  - **Modo visual "ASCII 333"**: tu portada y la música dibujadas con caracteres, con ondas en cada
    golpe y el espectro subiendo. Desde la v2.7.1 se arma en un arreglo de pixeles y se manda como una
    sola imagen (antes, miles de glifos por cuadro dejaban la app a ~1 fps; ahora va a 60).
  - **EQ para tu música**: presets paramétricos Rock pesado, Rock, Pop, Trap / Rap, Corridos,
    Batería al frente, Guitarras al frente y Lo-Fi / Chill (quitan lodo en vez de sólo subir todo), y
    **✦ Auto por género**: cada canción suena con el preset de su género.
  - **Celular más cómodo**: deslizar el mini reproductor a los lados cambia de canción y hacia
    arriba abre el reproductor; en el reproductor, la portada se desliza para cambiar y jalar hacia
    abajo lo cierra; menús como hoja desde abajo con la canción arriba; barra de abajo con pastilla;
    Buscar con título, pastilla de búsqueda y tus géneros para explorar; vibración en play, cambiar
    de canción y de pestaña.

- **v2.6, marca oscura**: logo nuevo, una calavera pirata cibernética (parche, ojo biónico, pistas de
  circuito y huesos cruzados) en negro, grafito, gris hueso y blanco; "FLACKER 333". Acento "Plata"
  de fábrica (los acentos de color de antes cambian a Plata una sola vez; se puede elegir otro).
  Las portadas generadas de artistas también van en grises con la calavera de marca de agua.
- **Compartir en historia** (botón ⇪ en el reproductor, en "Reproduciendo" y en el menú de cada
  canción), dos modos:
  - **Portada**: la portada, la canción y la calidad (p. ej. "FLAC · 24/96 · HI-RES"), en tres
    estilos: Aura, Vinilo y Póster.
  - **Letra** (como Spotify): tocas uno o varios versos seguidos (hasta 5) y sale la tarjeta con la
    letra en grande, en Color (el de la portada) o Negro. Si la canción está sonando, empieza en el
    verso de ese momento.
  En el celular abre directo el editor de historias de Instagram ("Otras apps" para el resto). En la
  PC la copia y la guarda en Imágenes\Flacker, o la manda a tu celular por Connect (misma red).
- **v2.4, diseño nuevo**: adiós "HI-FI · HACKER", tipografía Inter en
  todos los rótulos, barra de título oscura del color de la app, menús de clic derecho compactos
  como los de una app de PC, resaltado al pasar el mouse, portada y título que cambian con
  animación, play/pausa que se transforma y corazón que late al darle Me gusta.
- **Fotos de artista sin huecos**: si Navidrome sólo tiene su silueta gris, se busca la foto en
  Deezer; si tampoco hay, se genera una portada con degradado y sus iniciales.
- **Nexo 2029** (modo visual nuevo): el túnel caleidoscopio del visualizador del Xbox 360 llevado
  al futuro: vidrios en simetría que salen volando con cada golpe, un disco iridiscente al
  centro con el espectro alrededor y acabado de tele (barrido y separación de color).

- **Descubrir** (sección aparte; Inicio es sólo tu biblioteca): canciones, artistas y álbumes
  que **no tienes**, parecidos a lo que más escuchas y a tus favoritos, más "Lo que no tienes de …".
  Toca cualquier canción para oír una **muestra de 30 s** (o "Escuchar todas"). Catálogo de la
  API pública de Deezer; se refresca cada 12 h. La búsqueda también muestra "Fuera de tu biblioteca".
- **Aleatorio de toda tu música** (en la PC, el botón de la barra de arriba junto al ✨; en el celular, Inicio y Biblioteca): pone
  canciones al azar de toda la biblioteca y agrega más solas cuando se acaban.
- **Biblioteca**: artistas en cuadrícula con foto e índice A-Z; "Canciones" como tabla (título,
  álbum, duración) con filtro instantáneo, orden por título/artista/álbum/más oídas e índice A-Z.
  Al regresar de un álbum o artista, cada pantalla queda donde la dejaste.
- **PC**: el panel derecho se puede esconder (botón ⇥) para quedarte con el mini reproductor
  flotante en cualquier ancho; clic derecho en "Reproduciendo", el mini y la portada del
  reproductor completo abre el menú de la canción (playlist, radio, ir al álbum/artista…).
- **Todo sincronizado entre tus dispositivos**: Me gusta y playlists (viven en Navidrome) se
  recargan solos cada minuto y al instante cuando otro dispositivo de la misma red avisa que
  cambió algo. Las portadas propias y los ajustes compartidos (Last.fm, IA, color, modo visual)
  viajan por Connect entre dispositivos de la misma red; gana el cambio más reciente.
- **Letras confiables, sin tocar nada**: Navidrome si trae sincronizada; si no, LRCLIB. Se juntan
  todas las versiones que cuadran (mismo artista y título, duración ±3 s) y se califican: que sus
  tiempos quepan en la canción, misma duración, mismo álbum y que coincidan con la mayoría de las
  subidas. Gana la mejor; si ninguna sirve, mejor sin letra. Estilo Apple Music, con los tres
  puntitos en las partes instrumentales.
- **Detalles de PC**: al pasar el mouse las portadas se levantan con un botón de play directo y
  las canciones se iluminan (el número se vuelve ▶); transiciones de "empujar" entre pantallas.
- **Mi Last.fm**: tu perfil de Last.fm dentro de la app (usuario + API key gratis, se piden la
  primera vez): scrobbles, artistas, álbumes y canciones totales, lo que estás escuchando, gráfica
  de los últimos 30 días, tu reloj musical (horas) y días de la semana, tus tops de artistas,
  álbumes y canciones por periodo (7 días a siempre), tus géneros, scrobbles recientes y lo que te
  encanta. Lo que tengas en tu biblioteca se abre o suena directo; lo demás abre su página en
  Descubrir.
- **Álbum "dentro de la portada"**: el álbum muestra sólo el tracklist y lo básico; al tocar la
  portada (o ⓘ) se abre en grande con una tarjeta: reseña completa, lanzamiento, géneros, sello,
  formato y calidad, duración, tamaño, ReplayGain, créditos (producción, mezcla, composición…
  si tus archivos los traen) y cuántas veces lo has puesto.
- **Inicio**: destacados grandes, volver a escuchar, tus artistas, "Explora por género" y
  "Por década" (todo de tu biblioteca).
- **Playlists**: crear, renombrar, borrar, agregar canciones o álbumes enteros ("Agregar a
  playlist…" en el menú) y quitar canciones. Todo se guarda en Navidrome.
- **Portadas propias** para playlists, "Me gusta" y artistas (botón ✎ sobre la portada).
  Se guardan en cada dispositivo.
- **Biblioteca → Canciones**: todas tus rolas, cargando por páginas.
- **Una sola barra de búsqueda** (la de arriba en la PC), como en Spotify.
- **Modo visual**: el fondo de la app es el visualizador y baila con la música (bajo, medios,
  agudos y golpes). "Portada viva" (la portada derretida fluyendo, como Apple Music), Aurora,
  Pulso, Halo, Olas, Hiperespacio, Synthwave, Neón 360 y Nexo 2029, con brillo real (blur).
  Botón ✨ para encenderlo/apagarlo y elegir preset viéndolos en vivo.
- **Letra en su propio módulo** (como Apple Music): en la PC, el reproductor completo pone la
  letra a la derecha y la barra derecha tiene la pestaña "Letra"; en el celular, el botón de
  letra la pone en grande con la portada chiquita arriba.
- **Connect como Spotify**: al mandar la música a otro dispositivo, toda la app lo controla
  (play/pausa, siguiente, barra de tiempo, **volumen**, aleatorio, la cola y poner otras rolas),
  con la pastilla verde "Reproduciendo en …". Funciona en los dos sentidos (el volumen del
  celular es el de medios del sistema). "Este dispositivo" trae la música de regreso.
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
- Reseña del álbum: Last.fm → notas de Navidrome → Wikipedia (español, luego inglés) → Claude. Se guarda una vez por álbum.
- **Temas de color**: Plata, Ámbar, Rojo, Cian, Rosa, Menta, Violeta o "De la portada".
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
