"""
Manual de Flacker para beta testers (PDF).
    python tools/manual/make_manual.py [salida.pdf] [version]
Las capturas salen de las pruebas de diseño (DesignShotsTest) y viven en tools/manual/img.
"""
import os
import sys
from datetime import date

from reportlab.lib.colors import HexColor, white, black
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import inch
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (BaseDocTemplate, Frame, Image, KeepTogether, NextPageTemplate, PageBreak,
                                PageTemplate, Paragraph, Spacer, Table, TableStyle)

HERE = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(HERE, "img")
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "Flacker-Manual.pdf")
VERSION = sys.argv[2] if len(sys.argv) > 2 else "2.10"

F = r"C:\Windows\Fonts"
pdfmetrics.registerFont(TTFont("Seg", os.path.join(F, "segoeui.ttf")))
pdfmetrics.registerFont(TTFont("SegB", os.path.join(F, "segoeuib.ttf")))
pdfmetrics.registerFont(TTFont("SegBl", os.path.join(F, "seguibl.ttf")))
pdfmetrics.registerFont(TTFont("SegI", os.path.join(F, "segoeuii.ttf")))
pdfmetrics.registerFont(TTFont("Mono", os.path.join(HERE, "..", "..", "shared", "src", "commonMain", "composeResources", "font", "jetbrainsmono.ttf")))
pdfmetrics.registerFontFamily("Seg", normal="Seg", bold="SegB", italic="SegI", boldItalic="SegB")

INK = HexColor("#0B0B0D")
GRAY = HexColor("#6B6B73")
LINE = HexColor("#DADADF")
SOFT = HexColor("#F2F2F4")

W, H = letter
M = 0.85 * inch

st = {
    "h1": ParagraphStyle("h1", fontName="SegBl", fontSize=26, leading=30, textColor=INK, spaceBefore=4, spaceAfter=10),
    "h2": ParagraphStyle("h2", fontName="SegB", fontSize=14.5, leading=19, textColor=INK, spaceBefore=14, spaceAfter=5),
    "kicker": ParagraphStyle("k", fontName="Mono", fontSize=8.5, leading=11, textColor=GRAY, spaceAfter=2),
    "p": ParagraphStyle("p", fontName="Seg", fontSize=10.5, leading=15.5, textColor=INK, spaceAfter=7),
    "li": ParagraphStyle("li", fontName="Seg", fontSize=10.5, leading=15, textColor=INK, leftIndent=14, bulletIndent=2, spaceAfter=3),
    "cap": ParagraphStyle("cap", fontName="SegI", fontSize=8.5, leading=11, textColor=GRAY, alignment=TA_CENTER, spaceBefore=3, spaceAfter=10),
    "tip": ParagraphStyle("tip", fontName="Seg", fontSize=10, leading=14.5, textColor=INK),
    "cell": ParagraphStyle("cell", fontName="Seg", fontSize=9.8, leading=13.5, textColor=INK),
    "cellb": ParagraphStyle("cellb", fontName="SegB", fontSize=9.8, leading=13.5, textColor=INK),
}


def P(t, s="p"): return Paragraph(t, st[s])
def H1(t, kicker=None): return [P(kicker.upper(), "kicker")] if kicker else [] + [P(t, "h1")]
def H2(t): return P(t, "h2")
def LI(items): return [Paragraph(i, st["li"], bulletText="•") for i in items]
def NUM(items): return [Paragraph(i, st["li"], bulletText=f"{n}.") for n, i in enumerate(items, 1)]


def section(title, kicker):
    return [P(kicker.upper(), "kicker"), P(title, "h1")]


def tip(text, label="TIP"):
    t = Table([[Paragraph(f'<font name="Mono" size="8" color="#6B6B73">{label}</font><br/>{text}', st["tip"])]], colWidths=[W - 2 * M])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), SOFT), ("LINEBEFORE", (0, 0), (0, -1), 3, INK),
        ("LEFTPADDING", (0, 0), (-1, -1), 12), ("RIGHTPADDING", (0, 0), (-1, -1), 12),
        ("TOPPADDING", (0, 0), (-1, -1), 8), ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
    ]))
    return [Spacer(1, 4), t, Spacer(1, 8)]


def shot(name, caption, width=None):
    path = os.path.join(IMG, name + ".png")
    from PIL import Image as PImage
    w0, h0 = PImage.open(path).size
    w = width or (W - 2 * M)
    img = Image(path, width=w, height=w * h0 / w0)
    frame = Table([[img]], colWidths=[w])
    frame.setStyle(TableStyle([("BOX", (0, 0), (-1, -1), 0.6, LINE), ("LEFTPADDING", (0, 0), (-1, -1), 0),
                               ("RIGHTPADDING", (0, 0), (-1, -1), 0), ("TOPPADDING", (0, 0), (-1, -1), 0), ("BOTTOMPADDING", (0, 0), (-1, -1), 0)]))
    return [KeepTogether([Spacer(1, 4), frame, P(caption, "cap")])]


def keys(rows):
    data = [[Paragraph(f'<font name="SegB" size="9.5">{k}</font>', st["cell"]), Paragraph(v, st["cell"])] for k, v in rows]
    t = Table(data, colWidths=[1.9 * inch, W - 2 * M - 1.9 * inch])
    t.setStyle(TableStyle([
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, LINE), ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 0), (-1, -1), 5), ("BOTTOMPADDING", (0, 0), (-1, -1), 6), ("LEFTPADDING", (0, 0), (-1, -1), 2),
    ]))
    return [KeepTogether([t]), Spacer(1, 8)]


def table(header, rows, widths):
    data = [[Paragraph(h, st["cellb"]) for h in header]] + [[Paragraph(c, st["cell"]) for c in r] for r in rows]
    t = Table(data, colWidths=widths, repeatRows=1)
    t.setStyle(TableStyle([
        ("LINEBELOW", (0, 0), (-1, 0), 1.2, INK), ("LINEBELOW", (0, 1), (-1, -1), 0.5, LINE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"), ("TOPPADDING", (0, 0), (-1, -1), 5), ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("LEFTPADDING", (0, 0), (-1, -1), 3),
    ]))
    return [t, Spacer(1, 10)]


# ---------------------------------------------------------------- páginas

def cover(c, doc):
    c.saveState()
    c.setFillColor(black); c.rect(0, 0, W, H, fill=1, stroke=0)
    s = 3.3 * inch
    c.drawImage(os.path.join(IMG, "icon-ascii-512.png"), (W - s) / 2, H - 1.3 * inch - s, s, s, mask="auto")
    c.setFillColor(white); c.setFont("SegBl", 54)
    tw = c.stringWidth("FLACKER", "SegBl", 54)
    c.drawString((W - tw) / 2 - 12, H - 1.3 * inch - s - 0.95 * inch, "FLACKER")
    c.setFont("Mono", 14); c.setFillColor(HexColor("#9A9AA2"))
    c.drawString((W + tw) / 2 - 6, H - 1.3 * inch - s - 0.62 * inch, "333")
    c.setFont("Seg", 15); c.setFillColor(HexColor("#D8D8DE"))
    sub = "Manual para beta testers"
    c.drawString((W - c.stringWidth(sub, "Seg", 15)) / 2, H - 1.3 * inch - s - 1.35 * inch, sub)
    c.setFont("Mono", 9); c.setFillColor(HexColor("#7A7A82"))
    line = f"VERSIÓN {VERSION}  ·  WINDOWS + ANDROID  ·  {date.today().strftime('%d/%m/%Y')}"
    c.drawString((W - c.stringWidth(line, "Mono", 9)) / 2, 1.1 * inch, line)
    c.setStrokeColor(HexColor("#2A2A30")); c.setLineWidth(0.6); c.line(M, 1.35 * inch, W - M, 1.35 * inch)
    c.restoreState()


def page(c, doc):
    c.saveState()
    c.setFont("Mono", 7.5); c.setFillColor(GRAY)
    c.drawString(M, H - 0.55 * inch, "FLACKER 333  ·  MANUAL BETA")
    c.drawRightString(W - M, H - 0.55 * inch, f"v{VERSION}")
    c.setStrokeColor(LINE); c.setLineWidth(0.5); c.line(M, H - 0.62 * inch, W - M, H - 0.62 * inch)
    c.drawRightString(W - M, 0.5 * inch, str(doc.page - 1))
    c.restoreState()


story = [NextPageTemplate("body"), PageBreak()]

# 1 · Bienvenida
story += section("Qué onda", "00 · bienvenida")
story += [
    P("Gracias por probar <b>Flacker</b>. Es un reproductor de música para tu <b>propia</b> música: la que tienes en archivos "
      "(FLAC, MP3, etc.) o en un servidor Navidrome. No hay anuncios, no hay algoritmo vendiéndote cosas y suena en calidad "
      "original, sin comprimir."),
    P("Esta es una versión <b>beta</b>: ya hace mucho, pero seguro tiene detalles. Lo más valioso que puedes hacer es usarla "
      "como tu app de música de diario y contarnos qué se siente bien, qué se siente raro y qué truena. Al final viene "
      "cómo mandar tu retro."),
    H2("Lo que trae"),
]
story += LI([
    "<b>Tu música sin servidor:</b> elige una carpeta en la PC o usa la música guardada en tu cel.",
    "<b>Sonido:</b> ecualizador con presets para rock pesado, rock, pop, trap y más; <b>Auto por género</b>; "
    "<b>Volumen parejo</b> para que todas las rolas suenen igual de fuerte.",
    "<b>Visuales</b> estilo Xbox 360 que bailan con el bajo y una <b>pantalla completa</b> con la letra animada.",
    "<b>Letras sincronizadas</b> y <b>historias de Instagram</b> con la portada o un pedazo de la letra.",
    "<b>Radio</b> en vivo de nicho (groove ruso, techno francés, italo disco, city pop…) y <b>Géneros</b> de verdad, con mixes.",
    "<b>Arreglar metadatos</b> (PC): portadas, nombres, años, géneros y letras parejos en todos tus discos.",
    "<b>Transmitir a la tele</b> (Chromecast, Roku, teles DLNA) y <b>Flacker Connect</b> entre tu cel y tu PC.",
])
story += shot("d2-home", "Inicio en la PC.", width=4.9 * inch)

# 2 · Instalar
story += [PageBreak()] + section("Instalar", "01 · instalación")
story += [H2("Windows 10 / 11")]
story += NUM([
    "Descomprime <b>Flacker-Windows.zip</b> donde quieras, por ejemplo en <font name='Mono'>C:\\Programas</font>.",
    "Abre <b>Flacker\\Flacker.exe</b>. No necesitas instalar Java ni nada más: ya viene todo adentro.",
    "Si sale la pantalla azul de <b>Windows SmartScreen</b> (\"Windows protegió su PC\"): da clic en <b>Más información</b> "
    "y luego en <b>Ejecutar de todas formas</b>. Sale porque la app no está firmada (todavía), no porque tenga algo malo.",
    "Si Windows pregunta por el <b>firewall</b>, permite el acceso en <b>redes privadas</b>. Lo necesita para transmitir a la "
    "tele y para Flacker Connect.",
    "Opcional: clic derecho en Flacker.exe → <i>Enviar a</i> → <i>Escritorio (crear acceso directo)</i>.",
])
story += [H2("Android (8 o más nuevo)")]
story += NUM([
    "Pasa <b>Flacker.apk</b> al cel (WhatsApp, Telegram, Drive o cable) y ábrelo.",
    "Android te pedirá permiso para <b>instalar apps de origen desconocido</b> desde esa app (WhatsApp, Archivos…). Actívalo y regresa.",
    "Si <b>Play Protect</b> avisa que es una app desconocida, toca <b>Más detalles</b> → <b>Instalar de todas formas</b>.",
    "Para actualizar a una versión nueva, instala el APK nuevo encima: no se borra nada.",
])
story += tip("Los fondos animados más pesados (Caleidoscopio, Plasma, Túnel 360, Fractal y Estallido) se calculan en la tarjeta "
             "de video y en el cel piden <b>Android 13 o más nuevo</b>. En cels más viejos verás los demás efectos.")

# 3 · Primer arranque
story += [PageBreak()] + section("Tu música", "02 · primer arranque")
story += [P("Al abrirla sale la calavera de letras riéndose (un clic o toque la salta). La primera vez Flacker te pregunta de dónde "
            "sale tu música. Hay dos caminos:"), H2("A) Sin servidor (lo más fácil)")]
story += LI([
    "<b>En la PC:</b> toca <b>Usar mi música de una carpeta</b> y elige la carpeta donde tienes tus discos "
    "(por ejemplo <font name='Mono'>Música</font>). Lee FLAC, MP3, M4A, ALAC, OGG, WAV y DSD, con sus portadas "
    "(dentro del archivo o como <font name='Mono'>cover.jpg</font> / <font name='Mono'>folder.jpg</font>) y letras "
    "(<font name='Mono'>.lrc</font> junto a la canción).",
    "<b>En el cel:</b> toca <b>Usar la música de este celular</b> y da permiso para leer audio. Usa lo que ya tengas guardado.",
    "Después, en <b>Ajustes → Tu música</b>, puedes agregar o quitar carpetas y <b>Volver a leer</b> cuando metas discos nuevos.",
])
story += [H2("B) Con un servidor Navidrome")]
story += [P("Si algún día montas tu servidor (o alguien te comparte el suyo), pon la dirección, tu usuario y contraseña. "
            "Para escucharlo fuera de casa el servidor tiene que estar en internet (dominio con HTTPS) o en una VPN como Tailscale.")]
story += shot("d13-login", "Inicio de sesión: abajo está la opción “¿Sin servidor?”.", width=4.6 * inch)
story += tip("Las etiquetas importan: si tus archivos tienen bien puesto el artista, el álbum, el número de pista y la portada, "
             "la biblioteca se ve mucho más bonita. Si algo sale como “Desconocido”, casi siempre es la etiqueta del archivo.")

# 4 · Uso diario
story += [PageBreak()] + section("Lo de todos los días", "03 · uso")
story += LI([
    "<b>Inicio</b>: lo que escuchas seguido, discos recientes y recomendaciones de tu propia biblioteca.",
    "<b>Buscar</b>: canciones, discos y artistas mientras escribes.",
    "<b>Biblioteca</b>: álbumes, artistas, <b>géneros</b>, canciones, playlists, tus <b>Me gusta</b> y la <b>radio</b> "
    "(en el cel, Géneros y Radio están arriba en Biblioteca).",
    "<b>Clic derecho</b> (PC) o <b>mantener presionado</b> (cel) en una canción o disco: reproducir después, agregar a playlist, "
    "ir al artista, me gusta…",
])
story += shot("d3-album", "Página de un álbum.")
story += [PageBreak(), H2("Atajos en la PC")]
story += keys([
    ("Espacio", "Play / pausa (menos mientras escribes)"),
    ("Ctrl + → / ←", "Siguiente / anterior"),
    ("Ctrl + ↑ / ↓", "Subir / bajar volumen"),
    ("Alt + ← / →", "Atrás / adelante entre pantallas"),
    ("F11", "Pantalla completa (Esc para salir)"),
    ("L", "En pantalla completa: cambia entre Letra, Solo visual y Portada"),
    ("← → / ↑ ↓", "En pantalla completa: girar / acercar los visuales (también la rueda del mouse)"),
    ("+ / −  ·  F  ·  A", "Velocidad de los visuales · forma de las ondas · otro efecto"),
    ("N  ·  RePág / AvPág", "Siguiente / anterior escena del Xbox 360 (0 las regresa a cambiar solas)"),
    ("[ / ]", "Mover la letra 0.1 s (si va adelantada o atrasada)"),
    ("Teclas multimedia", "Funcionan aunque la app esté minimizada"),
])
story += [P("También aparece en el control multimedia de Windows (volumen / pantalla de bloqueo) y la vista previa de la barra de "
            "tareas trae anterior, pausa y siguiente. El botón de dos cuadritos de arriba a la derecha abre el <b>mini reproductor tipo iPod nano</b>, siempre encima.")]
story += [H2("Gestos en el cel")]
story += table(["Dónde", "Gesto", "Qué hace"], [
    ["Mini reproductor", "Deslizar a los lados", "Siguiente / anterior"],
    ["Mini reproductor", "Deslizar hacia arriba", "Abre el reproductor"],
    ["Reproductor", "Tocar la portada", "Play / pausa"],
    ["Reproductor", "Deslizar la portada", "Cambia de canción"],
    ["Reproductor", "Mantener y arrastrar", "“Raya el disco”: adelanta o regresa (vibra cada 5 s)"],
    ["Reproductor", "Jalar hacia abajo", "Cierra el reproductor"],
], [1.5 * inch, 1.75 * inch, W - 2 * M - 3.25 * inch])
story += tip("¿Prefieres botones? <b>Ajustes → Reproductor</b> regresa los botones de siempre.")

# 5 · Sonido
story += [PageBreak()] + section("Que suene machín", "04 · sonido")
story += LI([
    "<b>Ecualizador</b>: presets hechos para <b>Rock pesado, Rock, Pop, Trap / Rap, Corridos, Batería al frente, Guitarras al frente "
    "y Lo-Fi</b>. No sólo suben todo: quitan el lodo para que la batería y las guitarras peguen.",
    "<b>Auto por género</b>: cada canción suena con el preset de su género (usa la etiqueta de género del archivo).",
    "<b>Volumen parejo</b> (Ecualizador o Ajustes): <b>Parejo</b> deja todas las rolas al mismo nivel, como Spotify; <b>Fuerte</b> "
    "las sube bastante más (para la regadera) con un limitador para que no truene.",
    "<b>Calidad</b>: con archivos locales siempre suena el original. Con servidor puedes elegir calidad para Wi-Fi y datos.",
])

# 6 · Visuales
story += [H2("Modo visual y pantalla completa")]
story += [P("El fondo de la app puede volverse un visualizador que baila con tu música: elige uno en <b>Modo visual</b> "
            "(Nexo 2029, Neón 360, ASCII 333, Caleidoscopio, Plasma, Túnel 360, Fractal, Estallido y más). "
            "La <b>pantalla completa</b> (su botón arriba a la derecha o F11) mezcla efectos que cambian solos con los golpes, y tiene tres modos:")]
story += table(["Modo", "Qué ves"], [
    ["<b>Letra</b>", "La letra como video musical: cada verso entra con un estilo distinto (de golpe, inclinada, cayendo, desde la "
                    "cámara, máquina de escribir, karaoke delineado, una palabra gigante…). El coro se detecta solo y pega más fuerte."],
    ["<b>Solo visual</b>", "Sólo los efectos; al cambiar de canción aparece su nombre unos segundos."],
    ["<b>Portada</b>", "La portada en grande latiendo con el bajo, con la canción y su avance. Ideal para dejarla en la tele."],
], [1.3 * inch, W - 2 * M - 1.3 * inch])
story += [P("Arriba a la derecha (mueve el mouse o toca la pantalla) eliges el modo, <b>Fondo: Mezcla</b> o uno fijo, y el botón "
            "para transmitir. El fondo de arranque es <b>Xbox 360</b>: las piezas del visualizador \"Neon\" de Jeff Minter que traía el 360 "
            "(túneles con texturas que se doblan, la flor elástica \"Boingy\", las pirámides \"Star\", trazos en espejo y retroalimentación "
            "en cuadrantes, horizonte o espejo), combinadas en 32 escenas que cambian solas con la música. También están <b>Xbox 2001</b> "
            "(las ondas verde y azul de la primera Xbox) y <b>Neón 360</b>.")]
story += [KeepTogether([Image(os.path.join(IMG, "n360.png"), width=W - 2 * M, height=(W - 2 * M) * 540 / 1280), P("Algunas de las 32 escenas del modo Xbox 360.", "cap")])]
xb = Table([[Image(os.path.join(IMG, "v-geiss.png"), width=2.9 * inch, height=2.9 * inch * 9 / 16), Image(os.path.join(IMG, "v-neon.png"), width=2.9 * inch, height=2.9 * inch * 9 / 16)]], colWidths=[3.05 * inch, 3.05 * inch])
story += [KeepTogether([xb, P("Xbox 2001 y Neón 360.", "cap")])]
story += [H2("Con el control de Xbox (PC)")]
story += [P("Conecta un control de Xbox (cable, inalámbrico o Bluetooth) y, con Flacker al frente, manejas los visuales como en la consola:")]
story += keys([
    ("Cruceta", "Recorrer las escenas del Xbox 360 (como en la consola); Back las regresa a cambiar solas"),
    ("Stick izquierdo", "Girar y acercar"),
    ("Stick derecho", "Hacia dónde se va la estela y hacia dónde se dobla el túnel"),
    ("Gatillos RT / LT", "Más rápido / más lento"),
    ("X  ·  A", "Forma de las ondas · cambiar de efecto"),
    ("Y  ·  B", "Pantalla completa (otra vez: cambia el modo) · salir"),
    ("LB / RB  ·  Start", "Anterior / siguiente · play / pausa"),
    ("Back", "Regresar los visuales a como estaban"),
])
story += shot("d12-immersive-portada", "Pantalla completa en modo Portada.", width=4.6 * inch)

# 7 · Letras e Instagram
story += [PageBreak()] + section("Radio y géneros", "05 · explorar")
story += [P("<b>Radio</b> (en la PC, en la barra lateral junto a Álbumes y Artistas; en el cel, en Biblioteca, arriba): estaciones en vivo de todo el mundo. Hay nichos "
            "armados a mano (Groove ruso, Techno francés, French touch, Italo disco, Post-punk y darkwave, Shoegaze, City pop, Phonk, "
            "Rare groove…) y un buscador por género o por nombre. El corazón guarda la estación en <b>Tus estaciones</b>.")]
story += [P("<b>Géneros</b>: las etiquetas de los archivos muchas veces dicen \"Rock\" a todo. Flacker revisa cada disco en "
            "MusicBrainz (la base de datos abierta de música) y usa los géneros que vota la comunidad: un disco de shoegaze sale como "
            "Shoegaze, no como Rock. La primera vez tarda un rato (revisa un disco por segundo, en segundo plano) y después queda guardado. "
            "Cada género tiene su página con <b>Mix</b>, <b>Todo al azar</b>, <b>Radio del género</b>, artistas, álbumes y lo que más pones; "
            "en Inicio salen los <b>Mixes por género</b>.")]
row2 = Table([[Image(os.path.join(IMG, "d15-radio.png"), width=3.0 * inch, height=3.0 * inch * 860 / 1380), Image(os.path.join(IMG, "d14-genre.png"), width=3.0 * inch, height=3.0 * inch * 860 / 1380)]], colWidths=[3.1 * inch, 3.1 * inch])
story += [row2, P("Radio con sus nichos y la página de un género.", "cap")]
story += [H2("Arreglar metadatos (PC)")]
story += [P("¿Discos con portadas distintas, títulos con \"01 - track\", años de la reedición o sin género? En <b>Arreglar metadatos</b>:")]
story += NUM([
    "Agrega la carpeta donde están tus discos (la de tu música local ya aparece).",
    "Toca <b>Revisar mis discos</b>. Flacker lee tus archivos y busca cada disco en MusicBrainz por artista, nombre, número de "
    "canciones y duraciones.",
    "Cada disco dice <b>Ya está bien</b>, qué cambiaría (con qué tan seguro está) o <b>No lo encontré</b>. Ábrelo para ver el antes y el "
    "después: nombre, artista, año original, géneros, la portada nueva (1200 px) y cada canción.",
    "Elige qué aplicar (nombres, año, géneros, portada, <b>letras sincronizadas</b>) y toca <b>Aplicar</b>. "
    "<b>Aplicar los seguros</b> arregla de una vez todos los que cuadran casi perfecto.",
    "Si no te gustó, <b>Deshacer</b> regresa el último disco a como estaba (etiquetas, portada y letras).",
])
story += tip("Si usas Navidrome con la música en tu PC o NAS, agrega esa carpeta: al terminar, Flacker le pide al servidor que vuelva a "
             "leer los archivos. Si no encuentra un disco, búscalo a mano con <b>Buscar otra edición</b> (por ejemplo \"Air Moon Safari\").")
story += [Spacer(1, 10)] + section("Letras e historias", "06 · compartir")
story += LI([
    "Las letras salen de la etiqueta del archivo, de un <font name='Mono'>.lrc</font> junto a la canción o de internet. Si van "
    "adelantadas o atrasadas, en la pantalla de letra puedes moverlas.",
    "<b>Compartir en historia</b>: en el reproductor toca compartir y elige el diseño (portada, póster, vinilo) o un "
    "<b>pedazo de la letra</b> como Spotify. En el cel se abre Instagram directo; en la PC se copia la imagen y se guarda en "
    "<font name='Mono'>Imágenes\\Flacker</font>.",
])
row = Table([[
    Image(os.path.join(IMG, "d10-story-poster.png"), width=1.6 * inch, height=1.6 * inch * 16 / 9),
    Image(os.path.join(IMG, "d10-lyric-negro.png"), width=1.6 * inch, height=1.6 * inch * 16 / 9),
]], colWidths=[2.1 * inch, 2.1 * inch])
row.setStyle(TableStyle([("ALIGN", (0, 0), (-1, -1), "CENTER")]))
story += [Spacer(1, 6), row, P("Historia con la portada y con un pedazo de la letra.", "cap")]

# 8 · Tele
story += [PageBreak()] + section("Mandar la música a la tele", "07 · transmitir")
story += [P("Toca el botón de <b>dispositivos</b> (en el reproductor, en la barra de abajo de la PC o en la pantalla completa). "
            "En <b>Transmitir a la tele</b> aparecen los aparatos de tu Wi-Fi; toca uno y la música se pasa allá. "
            "Play, pausa, siguiente, la cola y el volumen se manejan desde Flacker. Para regresarla, toca <b>Este dispositivo</b>.")]
story += table(["Aparato", "Funciona", "Notas"], [
    ["Chromecast / tele con Chromecast (Google TV, Android TV)", "Sí", "Se abre el reproductor de Google con la portada y la canción."],
    ["Roku / tele Roku", "Sí", "Primero hay que darle permiso (ver abajo). Roku no deja adelantar a un segundo exacto ni subir el volumen "
                              "por red: eso va con el control de la tele."],
    ["Teles y bocinas DLNA (Samsung, LG, Sony, Denon, Yamaha, Kodi…)", "Sí", "Si una canción no le gusta, Flacker la manda convertida a MP3 "
                                                                            "(con servidor Navidrome)."],
    ["AirPlay (Apple TV, HomePod)", "Todavía no", "Está en la lista."],
], [2.4 * inch, 0.9 * inch, W - 2 * M - 3.3 * inch])
story += [H2("Si tu tele es Roku")]
story += [P("Las Roku vienen con el control por red en <b>Limitado</b> y así no aceptan que una app les mande música. En la tele:")]
story += NUM([
    "<b>Configuración</b> → <b>Sistema</b> → <b>Configuración avanzada del sistema</b>.",
    "<b>Control por apps móviles</b> → <b>Acceso a la red</b> → <b>Predeterminado</b>.",
])
story += tip("La tele tiene que estar prendida y en el <b>mismo Wi-Fi</b>. El audio sale de tu cel o PC hacia la tele, así que ese "
             "dispositivo debe quedarse en la red mientras suena (en el cel verás el aviso “Transmitiendo a …”). "
             "Si la tele no aparece, toca la flechita circular para buscar otra vez.")
story += [H2("Flacker Connect")]
story += [P("Con Flacker abierto en tu cel y en tu PC (misma red), cada uno ve al otro en el mismo botón: puedes pasar la música de "
            "uno a otro o usar el cel como control remoto de la PC.")]

# 9 · Problemas
story += [PageBreak()] + section("Si algo no jala", "08 · problemas comunes")
story += table(["Pasa esto", "Prueba esto"], [
    ["Windows no deja abrir la app", "SmartScreen: <b>Más información → Ejecutar de todas formas</b>."],
    ["No aparece la tele / el otro dispositivo", "Misma red Wi-Fi; en Windows permite Flacker en el firewall para redes privadas; "
                                                  "toca la flechita para buscar otra vez. Las VPN (Tailscale, etc.) pueden estorbar."],
    ["La Roku dice que no deja", "Pon <b>Control por apps móviles → Acceso a la red → Predeterminado</b>."],
    ["Faltan discos o salen como “Desconocido”", "Revisa las etiquetas de los archivos y toca <b>Ajustes → Tu música → Volver a leer</b>."],
    ["No salen portadas", "Que el archivo traiga portada o que haya un <font name='Mono'>cover.jpg</font> en la carpeta del disco."],
    ["La letra va desfasada", "Muévela en la pantalla de letra, o en pantalla completa con <b>[</b> y <b>]</b>. Con bocinas Bluetooth, "
                              "ajusta el retraso de los visuales en Ajustes: la letra lo usa también."],
    ["Un disco salió con datos raros", "<b>Arreglar metadatos</b> → ábrelo → <b>Buscar otra edición</b>; o <b>Deshacer</b>."],
    ["Una estación de radio no suena", "Algunas se caen o cambian de dirección: prueba otra del mismo nicho."],
    ["Se oye bajito o muy distinto entre rolas", "Activa <b>Volumen parejo</b> (o <b>Fuerte</b>)."],
    ["Va lenta la pantalla completa", "Elige <b>Fondo</b> fijo con un efecto más ligero (Synthwave, Hiperespacio) o el modo Portada."],
], [2.3 * inch, W - 2 * M - 2.3 * inch])

# 10 · Retro
story += [PageBreak()] + section("Tu retro", "09 · beta")
story += [P("Lo que nos cuentes decide qué se arregla primero y hacia dónde va la app. No hay respuestas malas: si algo te "
            "aburre, te confunde o te encanta, también cuenta.")]
story += [H2("Pruébale esto")]
story += LI([
    "Instalar y arrancar con tu música (¿qué tan fácil fue? ¿cuánto tardó en leerla?).",
    "Una semana usándola como tu app principal: buscar, armar playlists, me gusta, la cola.",
    "Los presets de EQ con tu música y el <b>Volumen parejo</b>.",
    "La pantalla completa con letras (¿se ve chido? ¿qué estilo te gustó más y cuál no?).",
    "Mandar la música a la tele y compartir una historia.",
    "La radio (¿encontraste algo chido?), los mixes por género y, en la PC, <b>Arreglar metadatos</b> con algunos discos.",
    "Si tienes control de Xbox: la pantalla completa con el control.",
])
story += [H2("Cómo reportar un error")]
story += [P("Manda un mensaje con esto (una captura o un video corto ayudan muchísimo):")]
form = Table([[Paragraph(
    "<font name='Mono' size='9'>"
    "VERSIÓN:  (Ajustes, hasta abajo)<br/>"
    "EQUIPO:   (ej. Windows 11 / Samsung A54 Android 14)<br/>"
    "QUÉ HICE: (pasos, uno por uno)<br/>"
    "QUÉ PASÓ: <br/>"
    "QUÉ ESPERABA: <br/>"
    "¿SIEMPRE PASA? (sí / a veces / una vez)"
    "</font>", st["tip"])]], colWidths=[W - 2 * M])
form.setStyle(TableStyle([("BOX", (0, 0), (-1, -1), 0.8, INK), ("LEFTPADDING", (0, 0), (-1, -1), 14),
                          ("TOPPADDING", (0, 0), (-1, -1), 10), ("BOTTOMPADDING", (0, 0), (-1, -1), 12)]))
story += [form, Spacer(1, 8)]
story += tip("Tu música, tus archivos y tus datos se quedan en tus dispositivos. Flacker no tiene cuentas, ni anuncios, ni manda "
             "lo que escuchas a ningún lado (salvo a tu propio servidor o a Last.fm, si tú lo configuras).", "PRIVACIDAD")
story += [H2("Y unas preguntas rápidas")]
story += NUM([
    "¿Qué es lo que <b>más</b> te gustó? ¿Y lo que menos? ¿A quién se la recomendarías?",
    "¿Qué te falta para usarla en lugar de Spotify / Apple Music / YouTube Music?",
    "¿Hubo algo que no encontraste o no entendiste cómo usar?",
    "Si Flacker fuera de paga, ¿pagarías? ¿Prefieres un pago único o una suscripción? ¿Cuánto te parecería justo?",
])



doc = BaseDocTemplate(OUT, pagesize=letter, leftMargin=M, rightMargin=M, topMargin=0.95 * inch, bottomMargin=0.85 * inch,
                      title="Flacker · Manual para beta testers", author="Flacker", subject=f"Versión {VERSION}")
frame = Frame(M, 0.85 * inch, W - 2 * M, H - 1.8 * inch, id="f")
doc.addPageTemplates([
    PageTemplate("cover", frames=[Frame(0, 0, W, H, id="c")], onPage=cover),
    PageTemplate("body", frames=[frame], onPage=page),
])
story.insert(0, Spacer(1, 1))
doc.build(story)
print(OUT)
