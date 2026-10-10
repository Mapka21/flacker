"""Sirve tools/fake-navidrome/rest en http://127.0.0.1:8765 con soporte de Range (seek)."""
import http.server
import os
import re

ROOT = os.path.dirname(os.path.abspath(__file__))


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def log_message(self, *a):
        pass

    def send_head(self):
        # Last.fm falso: /2.0/?method=user.getinfo&... -> lastfm/user.getinfo
        m = re.search(r"[?&]method=([\w.]+)", self.path)
        if self.path.startswith("/2.0") and m:
            path = os.path.join(ROOT, "lastfm", m.group(1).lower())
        else:
            path = self.translate_path(self.path.split("?")[0])
        if not os.path.isfile(path):
            self.send_error(404)
            return None
        size = os.path.getsize(path)
        f = open(path, "rb")
        start, end = 0, size - 1
        m = re.match(r"bytes=(\d*)-(\d*)", self.headers.get("Range") or "")
        if m:
            start = int(m.group(1) or 0)
            end = int(m.group(2)) if m.group(2) else size - 1
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        else:
            self.send_response(200)
        ctype = {"stream": "audio/wav", "getCoverArt": "image/png"}.get(os.path.basename(path), "application/json")
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        f.seek(start)
        self._left = end - start + 1
        return f

    def copyfile(self, src, dst):
        left = self._left
        while left > 0:
            b = src.read(min(65536, left))
            if not b:
                break
            dst.write(b)
            left -= len(b)


if __name__ == "__main__":
    print("Navidrome falso en http://127.0.0.1:8765")
    http.server.ThreadingHTTPServer(("127.0.0.1", 8765), Handler).serve_forever()
