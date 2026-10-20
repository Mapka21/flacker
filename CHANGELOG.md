# Historial de cambios

Lo más nuevo arriba. Las descargas de cada versión están en [Releases](https://github.com/Mapka21/flacker/releases).

- **v3.1**:
  - **Tu perfil**: en la PC tu avatar arriba a la derecha (como Spotify) abre Perfil, Mi Last.fm, Ecualizador,
    Arreglar metadatos, Ajustes y Cerrar sesión (ahora pide confirmación). La página de **Perfil** junta tu foto
    (la eliges con ✎ y pasa a tus otros dispositivos; si no, la de Last.fm o tu inicial), tus números, tu Last.fm,
    las herramientas y tus dispositivos. En el celular, la pestaña "Last.fm" se volvió **Perfil**.
  - **Barra lateral sólo con música**: Inicio, Descubrir, Radio y tu biblioteca; lo demás vive en el menú del avatar.
  - **La barra de arriba ya no se encima** con la ventana angosta (p. ej. 1920 px al 150 %, con la columna
    derecha): el buscador se encoge y los botones que no caben se van a un menú **⋯**. Botón **Adelante** junto a
    Atrás (se apagan cuando no hay a dónde ir).
  - **Botones más claros**: cada botón de ícono dice qué hace al pasar el mouse, y la barra de arriba se lee
    mejor sobre portadas claras.
  - Repo público con licencia GPL-3.0, README nuevo y biblioteca de muestra para las capturas
    (`tools/fake-navidrome/showcase.py`).

- **v3.0.1**: el modo visual **ASCII 333** ya no truena con "Index 64 out of bounds" al reproducir (pasaba si la
  ventana cambiaba de alto pero no de ancho, en el primer golpe fuerte de la canción).

- **v3.0 (estable)**:
  - **Transmitir a la tele ya toca todo**: si la tele no acepta el archivo (no lo abre, o suena un segundo y se
    para), Flacker lo convierte a WAV ahí mismo (ffmpeg en la PC, los decodificadores de Android en el cel) y se lo
    vuelve a mandar; también **sin servidor**, que antes no tenía a qué convertir. Las hi-res bajan a 44.1/48 kHz
    para la tele, se puede adelantar (también en Roku) y las teles DLNA (Samsung, LG…) reciben los datos que piden.
    Con Tailscale prendido ya no se le da a la tele la dirección de la VPN.
  - **PC**: ya no se queda muda al poner, en pausa, una canción con otra frecuencia (p. ej. de 44.1 a 96 kHz).
    Mover el volumen o un slider ya no escribe al registro de Windows en cada movimiento.
  - **Aleatorio**: "Reproducir a continuación" suena de verdad justo después de la actual (PC y celular).
  - **Connect más seguro**: la clave de las órdenes ya no es la que se anuncia a toda la red, y las API keys
    (Anthropic, Last.fm) sólo viajan entre dispositivos que iniciaron sesión con tu contraseña. **Después de
    actualizar, cierra sesión y vuelve a entrar en cada dispositivo** para que se vuelvan a ver. Sin servidor, cada
    dispositivo es su propia biblioteca (ya no se mezclan dos Flacker locales en la misma red).
  - **Canciones**: "Reintentar" funciona, carga rápido con bibliotecas grandes y tocar una canción ya no mete
    toda la biblioteca a la cola (va desde ahí, hasta 500).
  - **Celular**: con gestos, en la letra y la cola salen los botones (ahí no hay portada que tocar). Buscar muestra
    tus géneros de verdad (MusicBrainz) y abre su página.
  - Detalles: el error de búsqueda ya no se queda pegado; radio y muestras no muestran "me gusta"; la portada
    que gira en la píldora no gasta CPU en pausa; salir del modo sin servidor apaga su servidor.

- **v2.12.1**:
  - **Roku de verdad**: el "Roku Media Player" no acepta música de otras apps (Roku nunca abrió eso),
    así que Flacker trae su propia app para la tele (`cast/RokuChannel.kt`, SceneGraph): se instala
    una vez con el modo desarrollador (Flacker la sube con la contraseña, Digest) y recibe cada canción
    por ECP (`/launch/dev`, luego `/input` sin reabrir), con portada, nombre y avance en la tele.
  - **Arreglar metadatos**: la revisión ya no se cancela al cambiar de sección ("rememberCoroutineScope
    left the composition"); corre aparte, con **Detener** y tiempo restante, y se pueden aplicar los
    listos mientras sigue. Los discos que ya traen ID de MusicBrainz (Picard) no se buscan; una sola
    consulta por disco si cuadra. jaudiotagger ya no llena la consola (el logger se soltaba).

- **v2.12**:
  - **Modo Xbox 360** (el fondo de arranque en pantalla completa): las piezas del visualizador "Neon" de
    Jeff Minter según su manual (minotaurproject.co.uk): túnel en la GPU con dos texturas que ruedan y
    curvas que viajan hacia ti (redondo o cuadrado), **Boingy** (membrana elástica en simetría 6/8),
    **Star** (anillos de pirámides), **Psychedelia** (pincel en espejo) y **retroalimentación** con
    intensidad, escala y giro en modos normal, espejo, color que gira, cuadrantes y horizonte; la
    cámara gira y se acerca en los picos. 32 escenas en una matriz que cambia sola (o con la cruceta del
    control, N y RePág/AvPág). 60 fps.
  - **Interfaz**: tarjetas monocromas con mosaico de portadas (sin colores al azar), Géneros por familia
    con página nueva (portada difuminada, Mix, Radio, artistas, lo que más pones), Radio con "Sonando"
    en vivo, tus estaciones en fila y arte generado por nicho, resumen en Arreglar metadatos.
  - **Barra lateral**: Arreglar metadatos arriba con Last.fm y el Ecualizador; Géneros y Radio en
    Biblioteca con Álbumes y Artistas. En el cel, Géneros y Radio arriba en Biblioteca, y el título de
    Biblioteca ya no se aplasta.

- **v2.11**:
  - **Logo nuevo**: calavera pirata con huesos cruzados hecha de letras y números al azar (una sola
    figura matemática: `JollyRoger.kt` / `tools/brand/jolly.py`); sólida en los íconos chicos y de
    letras en los grandes. **Animación de entrada**: la calavera se arma, se ríe moviendo la mandíbula
    y escribe "FLACKER 333" (clic o toque la salta; `-Dflacker.noSplash=true` la quita).
  - **Radio** (radio-browser.info): 17 nichos curados (Groove ruso, Techno francés, French touch,
    Minimal y dub techno, Italo disco, Post-punk y darkwave, Shoegaze, City pop, Synthwave, Phonk,
    Rare groove…), buscador por género o nombre, estaciones guardadas. En vivo en PC y celular (en
    Android la radio no entra a la caché).
  - **Géneros de verdad**: cada álbum se revisa en MusicBrainz (géneros votados por la comunidad,
    normalizados a ~250 nombres y 17 familias) y se guarda; un disco de shoegaze ya no sale como
    "Rock". Sección **Géneros** por familia, página de cada género (Mix, Todo al azar, Radio del
    género, artistas, álbumes, lo que más pones) y **Mixes por género** en Inicio.
  - **Arreglar metadatos** (PC): revisa tus carpetas contra MusicBrainz y propone nombres, número de
    pista, año original, géneros, portada 1200 px (Cover Art Archive) y letras sincronizadas (LRCLIB →
    `.lrc`), con antes/después por disco, "Aplicar los seguros" y **Deshacer** (se guarda una copia de
    las etiquetas y la portada). Con Navidrome pide `startScan`; en modo local vuelve a leer.
  - **Visuales Xbox**: **Xbox 2001** (tipo Geiss, ondas verde y azul con estela) y **Neón 360**
    rehecho a la manera de Jeff Minter (simetría de espejo, arcos parabólicos, chorros de partículas
    que explotan con el bajo). Se **manejan con el control de Xbox** (XInput, sólo con la app al frente),
    las flechas, +/− y la rueda del mouse en pantalla completa.
  - **Letras**: soporte de LRC con tiempo por palabra (`<mm:ss.xx>`), el retraso de audio de los
    visuales (Bluetooth) aplicado también a la letra y **[ / ]** para moverla al momento.

- **v2.10**:
  - **Transmitir a la tele**: en el botón de dispositivos, "Transmitir a la tele" encuentra
    **Chromecast** (y teles con Chromecast), **Roku** y teles/bocinas **DLNA** de tu Wi-Fi. La cola,
    play/pausa, siguiente y el volumen se manejan desde Flacker; "Este dispositivo" la regresa donde
    iba. El audio le llega a la tele desde este dispositivo (un puente con clave al azar), así que
    funciona con Navidrome por Tailscale y en modo sin servidor. Si un aparato no acepta el archivo
    original, se manda en MP3 320 (con servidor). En Android un aviso mantiene viva la app mientras
    transmite. Las Roku piden "Control por apps móviles → Acceso a la red → Predeterminado".
    La búsqueda pregunta por cada adaptador de red (antes una VPN como Tailscale se llevaba el multicast).
  - **Pantalla completa con modos**: **Letra**, **Solo visual** y **Portada** (la portada en grande
    latiendo con el bajo, ideal para la tele), **Fondo: Mezcla** o uno fijo, y el botón para transmitir.
    En la PC, **L** cambia de modo. Se recuerda lo que elegiste.
  - **Más bajo**: la cámara respira con el bajo, brinca y tiembla en los golpes, un resplandor al centro
    late con él, y los fondos (shaders y figuras) reaccionan bastante más.
  - **Letras**: fuera el glitch; entran **Golpe** (cada palabra cae y sacude el verso), **Ola** (las
    palabras flotan con el bajo) y **Una palabra** gigante a la vez. El verso crece despacito mientras
    dura y late con el bajo; letra más grande.
  - **Manual para beta testers** (`tools/manual/make_manual.py` → `Flacker-Manual.pdf`).

- **v2.9.1**:
  - **Fondos nuevos en la tarjeta de video** (estilo Xbox 360): Caleidoscopio, Plasma, Túnel 360,
    Fractal y Estallido. Se calculan pixel por pixel en la GPU (shaders), así que van a 60 fps aunque
    llenen la pantalla. En Android piden Android 13 o más nuevo.
  - **Pantalla completa con más variedad**: el fondo cambia entre 10 efectos con figuras encima (o
    solo), con tres tipos de cambio (atravesar, salir disparado o disolverse) y un destello; cuando
    entra el coro también cambia.
  - **Letra a los fps de la pantalla**: la posición de la canción se suaviza cada cuadro (antes
    brincaba ~20 veces por segundo) y todo se anima sin volver a armar la pantalla.
  - **10 estilos de letra** que no se repiten seguidos: palabra por palabra desde grande, inclinado,
    una palabra por renglón, cayendo con rebote, desde la cámara, máquina de escribir, delineado que
    se rellena (karaoke), regado, glitch RGB. Las palabras largas tardan más (el tiempo se reparte por
    letras), el coro se detecta solo y pega más fuerte, las partes instrumentales muestran puntos que
    laten, y un verso viejo se desvanece si falta mucho para el siguiente.

- **v2.9**:
  - **Sin servidor**: en el inicio de sesión, "¿Sin servidor?" → **Usar mi música de una carpeta**
    (PC) o **Usar la música de este celular** (Android). Lee tus archivos (FLAC, MP3, M4A, ALAC, OGG,
    WAV, DSD…) con etiquetas, portadas (incrustadas o `cover.jpg`/`folder.jpg`), calidad y letras
    (`.lrc` al lado o en la etiqueta). Por dentro levanta un "Navidrome de bolsillo" en `127.0.0.1`,
    así que todo funciona igual: álbumes, artistas, buscar, me gusta, playlists, radio, letras, EQ e
    historias. En Ajustes → Tu música agregas o quitas carpetas, vuelves a leer o regresas a un
    servidor. Me gusta, playlists y conteos se guardan en el dispositivo (sin Last.fm en este modo).
  - **Pantalla completa** (botón ⛶ arriba, en el reproductor, o **F11**; **Esc** o doble clic para
    salir): mezcla dos efectos a la vez (Nexo, Neón 360, Hiperespacio, Halo, Pulso, Olas, Synth, ASCII)
    que cambian cada 12 s o en un golpe fuerte, cámara que late con el bajo, destellos y la **letra
    animada** palabra por palabra con estilos que cambian por línea. En el celular esconde las barras
    del sistema y no deja que se apague la pantalla.
  - **ASCII 333 v2**: cada cuadro nuevo a 60 fps, colores de tu portada por renglón, lluvia tipo
    Matrix que se acelera con los agudos, glitch en los golpes y zoom con el bajo.
  - **Más fluido**: los efectos de pixeles ya no crean una imagen nueva por cuadro (eso trababa la
    app segundos enteros con el recolector de basura); medido a 60 fps en pantalla completa.

- **v2.8**:
  - **Volumen parejo** (Ecualizador y Ajustes): Parejo (−14 LUFS, como Spotify) o **Fuerte**
    (−8 LUFS, para la regadera). Mide la sonoridad en vivo, sube o baja despacio para no bombear y
    un limitador con saturación suave evita que truene. Funciona con el EQ apagado.
  - **Reproductor por gestos en el celular** (Ajustes → Reproductor para regresar los botones):
    tocar la portada pausa, deslizar cambia de canción, **mantener y arrastrar "raya el disco"**
    para adelantar (vibra cada 5 s y muestra el tiempo), jalar hacia abajo cierra.
  - **Scrobbles sin pérdida**: lo que escuchas sin red se guarda con su hora y se manda solo al
    volver la conexión (Android avisa cuando hay Wi-Fi/datos; también se reintenta cada 2 min).
    En Ajustes → Servidor ves cuántos esperan.
  - **"Sin conexión"** (Descargas) como otra biblioteca: buscador, Álbumes en cuadrícula, Artistas
    y Canciones, cada álbum con su página; lo que se baja va plegado arriba. Sin red, la pantalla de
    error ofrece "Escuchar mis descargas".
  - **Historias sin marca**: sólo la portada, la canción, el artista y el álbum.

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
