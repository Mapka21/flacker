"""Calavera pirata cibernética de Flacker (viewBox 100x100). Huecos sin traslapes (relleno par-impar)."""
import os, re
from PIL import Image, ImageDraw, ImageChops

K = 0.5523


def circle(cx, cy, r):
    k = r * K
    return (f"M{cx - r:.2f},{cy:.2f} C{cx - r:.2f},{cy - k:.2f} {cx - k:.2f},{cy - r:.2f} {cx:.2f},{cy - r:.2f} "
            f"C{cx + k:.2f},{cy - r:.2f} {cx + r:.2f},{cy - k:.2f} {cx + r:.2f},{cy:.2f} "
            f"C{cx + r:.2f},{cy + k:.2f} {cx + k:.2f},{cy + r:.2f} {cx:.2f},{cy + r:.2f} "
            f"C{cx - k:.2f},{cy + r:.2f} {cx - r:.2f},{cy + k:.2f} {cx - r:.2f},{cy:.2f} Z")


def poly(*pts):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"


def thick(pts, w):
    """Polilínea gruesa con esquinas en inglete, como un solo contorno."""
    import math
    def norm(a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]; n = math.hypot(dx, dy); return (-dy / n, dx / n)
    left, right = [], []
    for i, p in enumerate(pts):
        if i == 0: nx, ny = norm(pts[0], pts[1]); m = 1
        elif i == len(pts) - 1: nx, ny = norm(pts[-2], pts[-1]); m = 1
        else:
            a = norm(pts[i - 1], p); b = norm(p, pts[i + 1])
            nx, ny = a[0] + b[0], a[1] + b[1]; l = math.hypot(nx, ny); nx, ny = nx / l, ny / l
            m = 1 / (nx * a[0] + ny * a[1])
        left.append((p[0] + nx * w / 2 * m, p[1] + ny * w / 2 * m)); right.append((p[0] - nx * w / 2 * m, p[1] - ny * w / 2 * m))
    return poly(*(left + right[::-1]))


def shorten(a, b, d):
    import math
    dx, dy = b[0] - a[0], b[1] - a[1]; n = math.hypot(dx, dy)
    return (a[0] + dx / n * d, a[1] + dy / n * d)


outline = ("M50,10 C30,10 18,23 18,40 C18,49 21,55 26,59 C27.5,60.2 28,61.5 28,63.5 L28,66 C28,68.8 30.2,71 33,71 "
           "L35,71 L35,77 C35,80.3 37.7,83 41,83 L59,83 C62.3,83 65,80.3 65,77 L65,71 L67,71 C69.8,71 72,68.8 72,66 "
           "L72,63.5 C72,61.5 72.5,60.2 74,59 C79,55 82,49 82,40 C82,23 70,10 50,10 Z")
# Parche + las dos cintas en un solo contorno (sin traslapes).
patch = ("M20.6,26.9 L54.6,34.4 C56,33.9 58,33.8 60.5,34 L69,34.9 C70.6,35.1 71.8,35.8 72.5,36.9 L80.6,34.4 "
         "L81.2,37.4 L73.1,39.9 L72.2,47.2 C71.5,51.5 67.8,54 63.5,54 C57.5,54 53.2,49.5 53.2,43.8 "
         "L53.8,37.5 L20.2,30.0 Z")
eye_hole = "M28.5,44 C28.5,38.6 33,35.6 38.5,36.1 C43.5,36.5 46.5,39.8 46,44.6 C45.5,49.5 41.5,52.5 37,52 C32,51.5 28.5,48.5 28.5,44 Z"
nose = ("M50,53.5 C48.5,56 46.8,58.8 46.6,60.4 C46.5,61.4 47.4,62 48.4,61.6 L50,60.8 L51.6,61.6 "
        "C52.6,62 53.5,61.4 53.4,60.4 C53.2,58.8 51.5,56 50,53.5 Z")
mouth = poly((36, 74.6), (64, 74.6), (64, 76.1), (36, 76.1))
teeth = []
for x in (40.4, 45.0, 49.25, 53.5, 58.1):
    teeth.append(poly((x, 71.2), (x + 1.5, 71.2), (x + 1.5, 74.6), (x, 74.6)))
    teeth.append(poly((x, 76.1), (x + 1.5, 76.1), (x + 1.5, 80.6), (x, 80.6)))
# Pistas de circuito: punto, hueco, línea (no se tocan).
r = 1.9
t1 = [(55, 18), (64, 18), (69, 23), (69, 29.5)]
t1s = [shorten(t1[0], t1[1], r + 0.9)] + t1[1:-1] + [shorten(t1[-1], t1[-2], r + 0.9)]
t2 = [(26.8, 24.2), (31.6, 19.4), (40.5, 19.4)]
t2s = [shorten(t2[0], t2[1], r + 0.9), t2[1], shorten(t2[-1], t2[-2], r + 0.9)]
circuits = [thick(t1s, 1.5), circle(55, 18, r), circle(69, 29.5, r), thick(t2s, 1.5), circle(26.8, 24.2, r), circle(40.5, 19.4, r)]

skull = " ".join([outline, patch, eye_hole, nose, mouth, *teeth, *circuits])
eye = circle(37.4, 44.0, 4.7) + " " + circle(37.4, 44.0, 3.1) + " " + circle(37.4, 44.0, 1.4)
bones = [thick([(20, 79), (80, 97)], 5.5), thick([(80, 79), (20, 97)], 5.5),
         circle(18.6, 76.6, 3.3), circle(15.6, 81.8, 3.3), circle(81.4, 76.6, 3.3), circle(84.4, 81.8, 3.3),
         circle(15.8, 94.6, 3.3), circle(21.0, 99.2, 3.3), circle(84.2, 94.6, 3.3), circle(79.0, 99.2, 3.3)]


def subpaths(d):
    toks = re.findall(r"[MLCZ]|-?\d+\.?\d*", d)
    out, cur, i, pos = [], [], 0, (0, 0)
    while i < len(toks):
        t = toks[i]
        if t == "M":
            if cur: out.append(cur)
            pos = (float(toks[i + 1]), float(toks[i + 2])); cur = [pos]; i += 3
        elif t == "L":
            pos = (float(toks[i + 1]), float(toks[i + 2])); cur.append(pos); i += 3
        elif t == "C":
            p = [float(x) for x in toks[i + 1:i + 7]]
            x0, y0 = pos
            for s in range(1, 13):
                u = s / 12
                a, b, c, e = (1 - u) ** 3, 3 * u * (1 - u) ** 2, 3 * u * u * (1 - u), u ** 3
                cur.append((a * x0 + b * p[0] + c * p[2] + e * p[4], a * y0 + b * p[1] + c * p[3] + e * p[5]))
            pos = (p[4], p[5]); i += 7
        elif t == "Z":
            i += 1
        else:
            pos = (float(toks[i]), float(toks[i + 1])); cur.append(pos); i += 2
    if cur: out.append(cur)
    return out


def evenodd_mask(d, N, s):
    m = Image.new("1", (N, N), 0)
    for sp in subpaths(d):
        one = Image.new("1", (N, N), 0)
        ImageDraw.Draw(one).polygon([(x * s, y * s) for x, y in sp], fill=1)
        m = ImageChops.logical_xor(m, one)
    return m.convert("L")


def preview(n=512):
    ss = 4; N = n * ss; s = N / 100
    im = Image.new("RGB", (N, N), (8, 8, 10))
    for b in bones:
        im.paste((130, 130, 138), mask=evenodd_mask(b, N, s))
    im.paste((244, 244, 246), mask=evenodd_mask(skull, N, s))
    im.paste((244, 244, 246), mask=evenodd_mask(eye, N, s))
    return im.resize((n, n), Image.LANCZOS)


if __name__ == "__main__":
    preview().save(os.path.join(os.environ["TEMP"], "skull-preview.png"))
    with open(os.path.join(os.environ["TEMP"], "skull-paths.kt"), "w", encoding="utf-8") as f:
        f.write(f'    const val SKULL = "{skull}"\n')
        f.write(f'    const val EYE = "{eye}"\n')
        f.write("    val BONES = listOf(\n" + "".join(f'        "{b}",\n' for b in bones) + "    )\n")
    print("ok")
