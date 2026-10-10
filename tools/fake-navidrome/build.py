"""
Servidor Subsonic/Navidrome falso para probar la app sin tocar el servidor real.

    python tools/fake-navidrome/build.py     # genera rest/ (JSON fijos, WAV 24/96 de prueba, portada)
    python tools/fake-navidrome/serve.py     # sirve en http://127.0.0.1:8765 (con Range, como Navidrome)

Cualquier usuario/contraseña sirve. Ver README → Pruebas.
"""
import json
import math
import os
import struct
import wave

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
REST = os.path.join(HERE, "rest")
os.makedirs(REST, exist_ok=True)


def resp(key=None, val=None):
    r = {"subsonic-response": {"status": "ok", "version": "1.16.1", "openSubsonic": True}}
    if key:
        r["subsonic-response"][key] = val
    return json.dumps(r, ensure_ascii=False)


def song(i, t):
    return {"id": f"s{i}", "title": t, "album": "Prueba Hi-Res", "albumId": "al1", "artist": "Marco Test", "artistId": "ar1",
            "track": i, "discNumber": 1, "year": 2026, "genre": "Electrónica", "coverArt": "al1", "suffix": "wav",
            "duration": 12, "bitRate": 4608, "samplingRate": 96000, "bitDepth": 24, "channelCount": 2,
            "replayGain": {"trackGain": -3.0, "albumGain": -4.0, "trackPeak": 0.8, "albumPeak": 0.85},
            "contributors": [{"role": "producer", "artist": {"id": "c1", "name": "Rick Rubin"}},
                             {"role": "mixer", "artist": {"id": "c2", "name": "Andrew Scheps"}},
                             {"role": "composer", "artist": {"id": "ar1", "name": "Marco Test"}}]}


songs = [song(1, "Barrido senoidal"), song(2, "Tono de 440 Hz")]
album = {"id": "al1", "name": "Prueba Hi-Res", "artist": "Marco Test", "artistId": "ar1", "coverArt": "al1",
         "songCount": 2, "duration": 24, "year": 2026, "genre": "Electrónica", "song": songs,
         "releaseDate": {"year": 2026, "month": 3, "day": 14}, "recordLabels": [{"name": "Laboratorio Flacker"}],
         "genres": [{"name": "Electrónica"}, {"name": "Ambient"}], "releaseTypes": ["Album"], "playCount": 12,
         "created": "2026-10-06T10:00:00Z", "played": "2026-10-09T22:00:00Z"}
alb_short = {k: v for k, v in album.items() if k != "song"}
lyrics = {"synced": True, "line": [{"start": i * 1500, "value": v} for i, v in enumerate(
    ["Este es un barrido", "de veinte hercios", "hasta veinte mil", "para probar", "las letras sincronizadas", "estilo Apple Music"])]}

playlist = {"id": "pl1", "name": "Para probar", "songCount": 2, "duration": 24, "coverArt": "al1", "entry": songs}
playlist_short = {k: v for k, v in playlist.items() if k != "entry"}

files = {
    "ping": resp(),
    "getAlbumList2": resp("albumList2", {"album": [alb_short]}),
    "getAlbum": resp("album", album),
    # Un artista favorito real para que "Descubrir" tenga de dónde partir (catálogo de Deezer).
    "getStarred2": resp("starred2", {"song": [songs[0]], "artist": [{"id": "ar9", "name": "Asking Alexandria"}]}),
    "getArtists": resp("artists", {"index": [{"name": "M", "artist": [{"id": "ar1", "name": "Marco Test", "albumCount": 1, "coverArt": "al1"}]}]}),
    "getArtist": resp("artist", {"id": "ar1", "name": "Marco Test", "albumCount": 1, "album": [alb_short]}),
    "getArtistInfo2": resp("artistInfo2", {"biography": "Artista de prueba para el motor de audio de escritorio."}),
    "getAlbumInfo2": resp("albumInfo", {"notes": "Álbum de prueba generado localmente: un barrido senoidal de 20 Hz a 20 kHz y un tono de 440 Hz, en WAV 24-bit / 96 kHz, para validar decodificación, EQ y visualizadores."}),
    "getRandomSongs": resp("randomSongs", {"song": songs}),
    "getSimilarSongs2": resp("similarSongs2", {"song": songs[::-1]}),
    "getTopSongs": resp("topSongs", {"song": songs}),
    "getPlaylists": resp("playlists", {"playlist": [playlist_short]}),
    "getGenres": resp("genres", {"genre": [{"value": "Electrónica", "songCount": 2, "albumCount": 1},
                                           {"value": "Ambient", "songCount": 2, "albumCount": 1},
                                           {"value": "Rock", "songCount": 2, "albumCount": 1}]}),
    "getPlaylist": resp("playlist", playlist),
    "createPlaylist": resp("playlist", playlist),
    "updatePlaylist": resp(), "deletePlaylist": resp(),
    "search3": resp("searchResult3", {"artist": [], "album": [alb_short], "song": songs}),
    "getLyricsBySongId": resp("lyricsList", {"structuredLyrics": [lyrics]}),
    "scrobble": resp(), "star": resp(), "unstar": resp(),
}
for k, v in files.items():
    with open(os.path.join(REST, k), "w", encoding="utf-8") as f:
        f.write(v)

# Last.fm falso (para la sección "Mi Last.fm"): un perfil con scrobbles del último mes.
import random, time
LF = os.path.join(HERE, "lastfm")
os.makedirs(LF, exist_ok=True)
rnd = random.Random(4)
now = int(time.time())
img = lambda u: [{"#text": u, "size": "extralarge"}]
cover = "http://127.0.0.1:8765/rest/getCoverArt"
artists = ["Asking Alexandria", "Bring Me the Horizon", "Peso Pluma", "Daft Punk", "Bad Bunny", "Sleep Token", "The Weeknd", "Deftones", "Natanael Cano", "Gorillaz"]
recent = []
for i in range(600):
    t = now - int(rnd.random() ** 1.3 * 30 * 86400)
    a = rnd.choice(artists)
    recent.append({"artist": {"name": a}, "name": f"Rola {i % 37}", "album": {"#text": f"Disco de {a}"}, "image": img(cover), "date": {"uts": str(t)}, "loved": "1" if i % 9 == 0 else "0"})
recent.sort(key=lambda x: -int(x["date"]["uts"]))
recent.insert(0, {"artist": {"name": "Daft Punk"}, "name": "Get Lucky", "album": {"#text": "Random Access Memories"}, "image": img(cover), "@attr": {"nowplaying": "true"}, "loved": "1"})
def lfw(name, obj):
    with open(os.path.join(LF, name), "w", encoding="utf-8") as f:
        json.dump(obj, f)
lfw("user.getinfo", {"user": {"name": "marcotest", "realname": "Marco", "country": "Mexico", "url": "https://www.last.fm/user/marcotest",
                             "playcount": "84213", "artist_count": "2311", "album_count": "5120", "track_count": "19873",
                             "registered": {"unixtime": str(now - 9 * 365 * 86400)}, "image": img(cover)}})
lfw("user.getrecenttracks", {"recenttracks": {"track": recent, "@attr": {"totalPages": "1", "total": str(len(recent))}}})
lfw("user.getlovedtracks", {"lovedtracks": {"track": [{"name": r["name"], "artist": {"name": r["artist"]["name"]}, "image": img(cover), "date": r.get("date", {"uts": str(now)})} for r in recent if r["loved"] == "1"][:20], "@attr": {"total": "642"}}})
lfw("user.gettopartists", {"topartists": {"artist": [{"name": a, "playcount": str(900 - i * 70)} for i, a in enumerate(artists)]}})
lfw("user.gettopalbums", {"topalbums": {"album": [{"name": f"Disco de {a}", "playcount": str(400 - i * 30), "artist": {"name": a}, "image": img(cover)} for i, a in enumerate(artists)]}})
lfw("user.gettoptracks", {"toptracks": {"track": [{"name": f"Rola {i}", "playcount": str(120 - i * 9), "artist": {"name": a}, "image": img(cover)} for i, a in enumerate(artists)]}})
lfw("artist.gettoptags", {"toptags": {"tag": [{"name": "metalcore", "count": "100"}, {"name": "rock", "count": "60"}, {"name": "electronic", "count": "40"}]}})

# 12 s de barrido log 20 Hz → 20 kHz, WAV 24-bit / 96 kHz estéreo a -12 dBFS.
sr = 96000
n = sr * 12
amp = 0.25 * (2 ** 23 - 1)
frames = bytearray()
ph = 0.0
for i in range(n):
    ph += 2 * math.pi * 20 * (1000 ** (i / n)) / sr
    b = struct.pack("<i", int(amp * math.sin(ph)))[:3]
    frames += b + b
with wave.open(os.path.join(REST, "stream"), "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(3)
    w.setframerate(sr)
    w.writeframes(bytes(frames))

img = Image.new("RGB", (600, 600))
d = ImageDraw.Draw(img)
for y in range(600):
    d.line([(0, y), (600, y)], fill=(40 + y // 6, 20, 120 + y // 5))
d.ellipse([150, 150, 450, 450], outline=(255, 176, 0), width=18)
img.save(os.path.join(REST, "getCoverArt"), "PNG")
print("listo:", REST)
