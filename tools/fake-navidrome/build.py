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
            "replayGain": {"trackGain": -3.0, "albumGain": -4.0, "trackPeak": 0.8, "albumPeak": 0.85}}


songs = [song(1, "Barrido senoidal"), song(2, "Tono de 440 Hz")]
album = {"id": "al1", "name": "Prueba Hi-Res", "artist": "Marco Test", "artistId": "ar1", "coverArt": "al1",
         "songCount": 2, "duration": 24, "year": 2026, "genre": "Electrónica", "song": songs}
alb_short = {k: v for k, v in album.items() if k != "song"}
lyrics = {"synced": True, "line": [{"start": i * 1500, "value": v} for i, v in enumerate(
    ["Este es un barrido", "de veinte hercios", "hasta veinte mil", "para probar", "las letras sincronizadas", "estilo Apple Music"])]}

playlist = {"id": "pl1", "name": "Para probar", "songCount": 2, "duration": 24, "coverArt": "al1", "entry": songs}
playlist_short = {k: v for k, v in playlist.items() if k != "entry"}

files = {
    "ping": resp(),
    "getAlbumList2": resp("albumList2", {"album": [alb_short]}),
    "getAlbum": resp("album", album),
    "getStarred2": resp("starred2", {"song": [songs[0]]}),
    "getArtists": resp("artists", {"index": [{"name": "M", "artist": [{"id": "ar1", "name": "Marco Test", "albumCount": 1, "coverArt": "al1"}]}]}),
    "getArtist": resp("artist", {"id": "ar1", "name": "Marco Test", "albumCount": 1, "album": [alb_short]}),
    "getArtistInfo2": resp("artistInfo2", {"biography": "Artista de prueba para el motor de audio de escritorio."}),
    "getAlbumInfo2": resp("albumInfo", {"notes": "Álbum de prueba generado localmente: un barrido senoidal de 20 Hz a 20 kHz y un tono de 440 Hz, en WAV 24-bit / 96 kHz, para validar decodificación, EQ y visualizadores."}),
    "getRandomSongs": resp("randomSongs", {"song": songs}),
    "getSimilarSongs2": resp("similarSongs2", {"song": songs[::-1]}),
    "getTopSongs": resp("topSongs", {"song": songs}),
    "getPlaylists": resp("playlists", {"playlist": [playlist_short]}),
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
