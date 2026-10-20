"""
Biblioteca de muestra para las capturas del README: artistas y discos inventados con portadas
generadas (nada con derechos de autor) y una pista de 3:30 con acordes y batería para que los
visualizadores tengan qué bailar.

    python tools/fake-navidrome/build.py      # base (Last.fm falso, etc.)
    python tools/fake-navidrome/showcase.py   # encima: la biblioteca de muestra
    python tools/fake-navidrome/serve.py

Para volver a la biblioteca de prueba normal: borra rest/ y corre build.py otra vez.
"""
import json
import math
import os
import random
import time
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REST = os.path.join(HERE, "rest")
LF = os.path.join(HERE, "lastfm")
FONT = os.path.join(HERE, "..", "..", "shared", "src", "commonMain", "composeResources", "font", "inter.ttf")
os.makedirs(REST, exist_ok=True)
os.makedirs(LF, exist_ok=True)
for f in os.listdir(REST):  # respuestas por id de una corrida anterior
    if "." in f:
        os.remove(os.path.join(REST, f))


def resp(key=None, val=None):
    r = {"subsonic-response": {"status": "ok", "version": "1.16.1", "openSubsonic": True}}
    if key:
        r["subsonic-response"][key] = val
    return json.dumps(r, ensure_ascii=False)


def put(name, text):
    with open(os.path.join(REST, name), "w", encoding="utf-8") as f:
        f.write(text)


# ------------------------------------------------------------------ portadas generadas

S = 800


def font(px, weight=800):
    f = ImageFont.truetype(FONT, px)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def grain(img, amount, rnd):
    a = np.asarray(img).astype(np.float32)
    n = np.random.default_rng(rnd.randint(0, 1 << 30)).normal(0, amount, a.shape[:2])[..., None]
    return Image.fromarray(np.clip(a + n, 0, 255).astype(np.uint8))


def mesh(rnd, pal):
    img = Image.new("RGB", (S, S), pal[0])
    d = ImageDraw.Draw(img)
    for c in pal[1:] * 2:
        r = rnd.randint(S // 4, S // 2)
        x, y = rnd.randint(0, S), rnd.randint(0, S)
        d.ellipse([x - r, y - r, x + r, y + r], fill=c)
    return grain(img.filter(ImageFilter.GaussianBlur(110)), 9, rnd)


def sun(rnd, pal):
    y = np.linspace(0, 1, S)[:, None]
    top, bot = np.array(pal[0]), np.array(pal[1])
    sky = (top * (1 - y) + bot * y)[:, None, :].repeat(S, 1).reshape(S, S, 3)
    img = Image.fromarray(sky.astype(np.uint8))
    d = ImageDraw.Draw(img)
    cx, cy, r = S // 2, int(S * 0.52), int(S * 0.28)
    for i in range(r * 2):
        yy = cy - r + i
        w = math.sqrt(max(0, r * r - (yy - cy) ** 2))
        band = yy > cy - r * 0.1 and (yy // 14) % 2 == 0
        if not band:
            t = i / (2 * r)
            col = tuple(int(pal[2][k] * (1 - t) + pal[3][k] * t) for k in range(3))
            d.line([(cx - w, yy), (cx + w, yy)], fill=col)
    hz = int(S * 0.70)
    d.rectangle([0, hz, S, S], fill=(10, 6, 18))
    for i in range(1, 14):
        yy = hz + int((i / 13) ** 2 * (S - hz))
        d.line([(0, yy), (S, yy)], fill=pal[2], width=2)
    for i in range(-12, 13):
        d.line([(S / 2 + i * 18, hz), (S / 2 + i * 120, S)], fill=pal[2], width=2)
    return grain(img, 6, rnd)


def pulses(rnd, pal):
    # Líneas de pulso apiladas (blanco sobre negro).
    img = Image.new("RGB", (S, S), pal[0])
    d = ImageDraw.Draw(img)
    rows = 46
    for j in range(rows):
        y0 = 150 + j * 11
        pts = []
        for i in range(0, 501, 4):
            x = 150 + i
            c = 1 - abs(i - 250) / 250
            amp = (c ** 3) * 70 * (0.4 + rnd.random())
            pts.append((x, y0 - abs(math.sin(i * 0.05 + j) * amp + rnd.gauss(0, amp * 0.15))))
        d.polygon(pts + [(650, y0 + 6), (150, y0 + 6)], fill=pal[0])
        d.line(pts, fill=pal[1], width=2)
    return grain(img, 5, rnd)


def bauhaus(rnd, pal):
    img = Image.new("RGB", (S, S), pal[0])
    d = ImageDraw.Draw(img)
    for _ in range(5):
        c = rnd.choice(pal[1:])
        x, y, r = rnd.randint(0, S), rnd.randint(0, S), rnd.randint(80, 260)
        kind = rnd.random()
        if kind < 0.4:
            d.ellipse([x - r, y - r, x + r, y + r], fill=c)
        elif kind < 0.7:
            d.rectangle([x - r, y - r // 3, x + r, y + r // 3], fill=c)
        else:
            d.pieslice([x - r, y - r, x + r, y + r], rnd.choice([0, 90, 180, 270]), rnd.choice([180, 270]), fill=c)
    return grain(img, 7, rnd)


def rings(rnd, pal):
    img = Image.new("RGB", (S, S), pal[0])
    d = ImageDraw.Draw(img)
    cx, cy = S * (0.35 + rnd.random() * 0.3), S * (0.35 + rnd.random() * 0.3)
    for i in range(40, 0, -1):
        r = i * 17
        t = i / 40
        col = tuple(int(pal[1][k] * t + pal[2][k] * (1 - t)) for k in range(3))
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=col, width=5)
    return grain(img.filter(ImageFilter.GaussianBlur(1.2)), 8, rnd)


def type_cover(rnd, pal, title):
    img = mesh(rnd, pal[:2] + [pal[0]])
    d = ImageDraw.Draw(img)
    words = title.upper().split()
    f = font(150 if max(len(w) for w in words) < 7 else 110)
    y = S - 60 - len(words) * 140
    for w in words:
        d.text((52, y), w, font=f, fill=pal[2])
        y += 140
    return img


def save(img, cover_id):
    img.save(os.path.join(REST, f"getCoverArt.{cover_id}"), "PNG", optimize=True)


# ------------------------------------------------------------------ la biblioteca

H = lambda s: tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))
ARTISTS = [
    ("Nocturno 333", "Synthwave", [("Luces de Neón", 2025, "sun", ["#14052b", "#ff3d7f", "#ffd34d", "#ff3d7f"]),
                                   ("Autopista", 2023, "sun", ["#03122b", "#0affd2", "#9a5cff", "#0affd2"])]),
    ("Velvet Static", "Shoegaze", [("Haze Garden", 2024, "mesh", ["#2a1030", "#d8a7ff", "#ff9aa2", "#6a4cff"]),
                                   ("Soft Collapse", 2022, "mesh", ["#0b1a24", "#7ad7ff", "#f0f0ff", "#356bff"])]),
    ("Marea Baja", "Indie", [("Sal y Ceniza", 2025, "type", ["#1d2b33", "#ff8a3d", "#f4efe6"])]),
    ("Ashen Choir", "Metalcore", [("Hollow Crowns", 2024, "pulses", ["#060606", "#e9e9e9"]),
                                  ("Black Tide", 2021, "rings", ["#0a0a0a", "#d9d9d9", "#2a2a2a"])]),
    ("Dahlia Vex", "Pop", [("Glass Heart", 2025, "mesh", ["#300818", "#ff4d8d", "#ffd1e3", "#8a2be2"])]),
    ("Kairo", "Electrónica", [("Midnight Transit", 2024, "rings", ["#020a14", "#3dd9eb", "#0c2440"]),
                              ("Frecuencias", 2023, "bauhaus", ["#efe9dc", "#e63b2e", "#1f4bd8", "#f2b705", "#111111"])]),
    ("Orquídea Negra", "Dream Pop", [("Lluvia en Domingo", 2024, "type", ["#141018", "#7b5cff", "#f5f0ff"])]),
    ("Selva Digital", "Cumbia", [("Trópico 2099", 2025, "bauhaus", ["#0f3d2e", "#ffcf3d", "#ff5a36", "#46e0a0", "#f6f1e7"])]),
    ("Los Cometas", "Indie Pop", [("Verano Infinito", 2023, "sun", ["#1b0b3a", "#ffb36b", "#fff0a8", "#ff6b6b"])]),
    ("Pulso", "Techno", [("Pulso Cero", 2025, "pulses", ["#0d0d0f", "#ff2d55"])]),
]
TRACKS = ["Intro", "Luz de Madrugada", "Ruido Blanco", "Ciudad Vacía", "Eco", "Mil Noches", "Cristal", "Después de Todo",
          "Gravedad", "Fantasma", "Lento", "Cielo de Neón", "Sin Señal", "Regreso", "Polvo de Estrellas", "Último Tren"]

rnd = random.Random(333)
now = int(time.time())
albums, artists, songs_all = [], [], []
n_al = 0
for ai, (artist, genre, discs) in enumerate(ARTISTS):
    arid = f"ar{ai + 1}"
    art_albums = []
    for title, year, style, pal in discs:
        n_al += 1
        alid = f"al{n_al}"
        pal_rgb = [H(c) for c in pal]
        r = random.Random(alid)
        img = {"mesh": lambda: mesh(r, pal_rgb), "sun": lambda: sun(r, pal_rgb), "pulses": lambda: pulses(r, pal_rgb),
               "bauhaus": lambda: bauhaus(r, pal_rgb), "rings": lambda: rings(r, pal_rgb),
               "type": lambda: type_cover(r, pal_rgb, title)}[style]()
        save(img, alid)
        n = r.randint(8, 12)
        names = r.sample(TRACKS, n)
        songs = []
        for i, t in enumerate(names):
            sid = f"{alid}s{i + 1}"
            hi = n_al % 3 == 0
            songs.append({"id": sid, "title": t, "album": title, "albumId": alid, "artist": artist, "artistId": arid,
                          "track": i + 1, "discNumber": 1, "year": year, "genre": genre, "coverArt": alid, "suffix": "flac",
                          "duration": 210, "bitRate": 2300 if hi else 1000, "samplingRate": 96000 if hi else 44100,
                          "bitDepth": 24 if hi else 16, "channelCount": 2, "playCount": r.randint(0, 80),
                          "replayGain": {"trackGain": -6.5, "albumGain": -7.0, "trackPeak": 0.98, "albumPeak": 0.99}})
        al = {"id": alid, "name": title, "artist": artist, "artistId": arid, "coverArt": alid, "songCount": n,
              "duration": 210 * n, "year": year, "genre": genre, "genres": [{"name": genre}], "releaseTypes": ["Album"],
              "playCount": r.randint(5, 300), "created": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now - n_al * 86400 * 9)),
              "played": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(now - r.randint(1, 40) * 3600)),
              "recordLabels": [{"name": "Discos 333"}]}
        put(f"getAlbum.{alid}", resp("album", {**al, "song": songs}))
        put(f"getAlbumInfo2.{alid}", resp("albumInfo", {"notes": f"{title} es el disco de {year} de {artist}: {genre.lower()} grabado de noche, "
                                                                   f"con sintetizadores viejos y guitarras llenas de reverb. Artista y disco inventados para las capturas de Flacker."}))
        albums.append(al)
        art_albums.append(al)
        songs_all += songs
    # "Foto" del artista: un acercamiento desenfocado de su primer disco.
    r = random.Random(arid)
    src = Image.open(os.path.join(REST, f"getCoverArt.{art_albums[0]['id']}"))
    z = S // 2
    x, y = r.randint(0, S - z), r.randint(0, S - z)
    photo = grain(src.crop((x, y, x + z, y + z)).resize((S, S), Image.LANCZOS).filter(ImageFilter.GaussianBlur(14)), 12, r)
    save(photo, arid)
    save(photo, f"ar-{arid}")  # Navidrome nombra así la imagen del artista
    a = {"id": arid, "name": artist, "albumCount": len(art_albums), "coverArt": arid}
    artists.append(a)
    put(f"getArtist.{arid}", resp("artist", {**a, "album": art_albums}))
    put(f"getArtistInfo2.{arid}", resp("artistInfo2", {"biography": f"{artist} hace {genre.lower()} desde algún lugar entre la Ciudad de México y internet. (Artista inventado para las capturas.)"}))

rnd.shuffle(songs_all)
by = lambda key: sorted(albums, key=key)
put("getAlbumList2", resp("albumList2", {"album": albums}))
put("getAlbumList2.newest", resp("albumList2", {"album": by(lambda a: a["created"])[::-1]}))
put("getAlbumList2.recent", resp("albumList2", {"album": by(lambda a: a["played"])[::-1]}))
put("getAlbumList2.frequent", resp("albumList2", {"album": by(lambda a: -a["playCount"])}))
put("getAlbumList2.random", resp("albumList2", {"album": random.Random(7).sample(albums, len(albums))}))
put("getAlbumList2.starred", resp("albumList2", {"album": albums[::3]}))
put("getAlbumList2.alphabeticalByName", resp("albumList2", {"album": by(lambda a: a["name"])}))
idx = {}
for a in sorted(artists, key=lambda a: a["name"]):
    idx.setdefault(a["name"][0].upper(), []).append(a)
put("getArtists", resp("artists", {"index": [{"name": k, "artist": v} for k, v in idx.items()]}))
liked = songs_all[:24]
put("getStarred2", resp("starred2", {"song": liked, "album": albums[::3], "artist": artists[:4]}))
put("getRandomSongs", resp("randomSongs", {"song": songs_all[:50]}))
put("getSimilarSongs2", resp("similarSongs2", {"song": songs_all[10:40]}))
put("getTopSongs", resp("topSongs", {"song": songs_all[:10]}))
genres = {}
for s in songs_all:
    g = genres.setdefault(s["genre"], {"value": s["genre"], "songCount": 0, "albumCount": 0})
    g["songCount"] += 1
for a in albums:
    genres[a["genre"]]["albumCount"] += 1
put("getGenres", resp("genres", {"genre": list(genres.values())}))
put("search3", resp("searchResult3", {"artist": artists[:3], "album": albums[:6], "song": songs_all[:12]}))
pls = [("pl1", "Para manejar de noche", 0), ("pl2", "Lunes gris", 5), ("pl3", "Gym 333", 9), ("pl4", "Domingo lento", 3)]
plist = []
for pid, name, off in pls:
    entry = songs_all[off:off + 18]
    p = {"id": pid, "name": name, "songCount": len(entry), "duration": 210 * len(entry), "coverArt": entry[0]["coverArt"], "owner": "marco"}
    plist.append(p)
    put(f"getPlaylist.{pid}", resp("playlist", {**p, "entry": entry}))
put("getPlaylists", resp("playlists", {"playlist": plist}))
lyrics = {"synced": True, "line": [{"start": i * 4200, "value": v} for i, v in enumerate([
    "Las luces de la ciudad", "se apagan una por una", "y yo sigo aquí despierto", "contando las que faltan",
    "el radio dice tu nombre", "en una frecuencia vieja", "si cierro los ojos", "todavía suena la noche",
    "Luces de neón", "en el retrovisor", "nada de esto es real", "pero suena mejor"])]}
put("getLyricsBySongId", resp("lyricsList", {"structuredLyrics": [lyrics]}))

# Last.fm falso con los mismos artistas.
lr = random.Random(4)
img = lambda cid: [{"#text": f"http://127.0.0.1:8765/rest/getCoverArt?id={cid}", "size": "extralarge"}]
recent = []
for i in range(600):
    s = lr.choice(songs_all[:60])
    recent.append({"artist": {"name": s["artist"]}, "name": s["title"], "album": {"#text": s["album"]}, "image": img(s["coverArt"]),
                   "date": {"uts": str(now - int(lr.random() ** 1.3 * 30 * 86400))}, "loved": "1" if i % 9 == 0 else "0"})
recent.sort(key=lambda x: -int(x["date"]["uts"]))


def lfw(name, obj):
    with open(os.path.join(LF, name), "w", encoding="utf-8") as f:
        json.dump(obj, f)


lfw("user.getinfo", {"user": {"name": "marco333", "realname": "Marco", "country": "Mexico", "url": "https://www.last.fm/",
                             "playcount": "84213", "artist_count": "2311", "album_count": "5120", "track_count": "19873",
                             "registered": {"unixtime": str(now - 9 * 365 * 86400)}, "image": img("al6")}})
lfw("user.getrecenttracks", {"recenttracks": {"track": recent, "@attr": {"totalPages": "1", "total": str(len(recent))}}})
lfw("user.getlovedtracks", {"lovedtracks": {"track": [{"name": r["name"], "artist": {"name": r["artist"]["name"]}, "image": r["image"], "date": r["date"]} for r in recent if r["loved"] == "1"][:20], "@attr": {"total": "642"}}})
lfw("user.gettopartists", {"topartists": {"artist": [{"name": a["name"], "playcount": str(900 - i * 70), "image": img(a["id"])} for i, a in enumerate(artists)]}})
lfw("user.gettopalbums", {"topalbums": {"album": [{"name": a["name"], "playcount": str(400 - i * 30), "artist": {"name": a["artist"]}, "image": img(a["id"])} for i, a in enumerate(albums)]}})
lfw("user.gettoptracks", {"toptracks": {"track": [{"name": s["title"], "playcount": str(120 - i * 9), "artist": {"name": s["artist"]}, "image": img(s["coverArt"])} for i, s in enumerate(songs_all[:10])]}})

# ------------------------------------------------------------------ la pista: 3:30 de synthwave

sr, bpm, secs = 44100, 100, 210
t = np.arange(sr * secs) / sr
beat = 60 / bpm
chords = [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]]  # Am F C G
hz = lambda m: 440 * 2 ** ((m - 69) / 12)
bar = (t // (beat * 4)).astype(int) % 4
out = np.zeros_like(t)
for ci, ch in enumerate(chords):
    on = bar == ci
    for m in ch + [ch[0] - 12]:
        f = hz(m)
        saw = 2 * ((t * f) % 1) - 1 + 0.5 * (2 * ((t * f * 1.003) % 1) - 1)
        out += on * saw * 0.06
pos = t % beat
kick = np.sin(2 * np.pi * (50 + 120 * np.exp(-pos * 30)) * pos) * np.exp(-pos * 9)
hat = np.random.default_rng(3).normal(0, 1, t.size) * np.exp(-((t + beat / 2) % beat) * 60) * 0.12
lead_n = [69, 72, 76, 74, 72, 69, 67, 69]
step = (t // (beat / 2)).astype(int) % len(lead_n)
lf = np.array([hz(m) for m in lead_n])[step]
lead = np.sin(2 * np.pi * np.cumsum(lf) / sr) * np.exp(-(t % (beat / 2)) * 4) * 0.18
mix = out + kick * 0.7 + hat + lead * (t > beat * 16)
mix *= np.clip(t / 2, 0, 1) * np.clip((secs - t) / 3, 0, 1)
mix = np.tanh(mix * 1.4) * 0.8
pcm = (np.stack([mix, np.roll(mix, 220)], 1) * 32767).astype("<i2")
with wave.open(os.path.join(REST, "stream"), "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(sr)
    w.writeframes(pcm.tobytes())
print(f"muestra lista: {len(albums)} discos, {len(artists)} artistas, {len(songs_all)} canciones")
