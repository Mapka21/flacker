"""
Íconos de Flacker con la calavera en arte ASCII, en todos los tamaños. En los chicos se usa una
cuadrícula con menos caracteres (más grandes) para que siga leyéndose como calavera.

    python tools/brand/make_icons.py

Genera: desktop/icon.ico, desktop/src/main/resources/icons/flacker-<n>.png (íconos de la ventana),
shared/.../drawable/app_icon.png y el frente del ícono adaptativo de Android.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import numpy as np  # noqa: E402

import jolly  # noqa: E402  (la calavera pirata de la marca, igual que JollyRoger.kt)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONT = os.path.join(ROOT, "shared/src/commonMain/composeResources/font/jetbrainsmono.ttf")
GRAPHITE, INK = (0x23, 0x24, 0x28), (0x07, 0x07, 0x08)
WHITE, BONE = (0xF4, 0xF4, 0xF6), (0x8A, 0x8A, 0x92)


def rows_for(size):
    """Renglones de letras según el tamaño final (sólo en los grandes; los chicos van sólidos)."""
    return 22 if size <= 160 else 28


def vgrad(w, h, top, bottom):
    g = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(g)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return g


def ascii_layer(lines, box_w, box_h, ss):
    """Máscara (L) con el texto ASCII centrado en una caja de box_w × box_h (ya supermuestreada)."""
    rows = len(lines); cols = max(len(l) for l in lines)
    # Tamaño de letra para que quepa: alto de renglón = tamaño; ancho de carácter ≈ 0.6 × tamaño.
    fs = int(min(box_h / rows, box_w / (cols * 0.6)))
    font = ImageFont.truetype(FONT, fs)
    try:
        font.set_variation_by_axes([800])
    except Exception:
        pass
    adv = font.getlength("M")
    m = Image.new("L", (box_w, box_h), 0)
    d = ImageDraw.Draw(m)
    tw = adv * cols; th = fs * rows
    x0 = (box_w - tw) / 2; y0 = (box_h - th) / 2
    for i, line in enumerate(lines):
        d.text((x0, y0 + i * fs), line, font=font, fill=255)
    return m


def solid_layer(box, ss):
    """Calavera blanca y huesos gris hueso, sólidos (íconos chicos: se leen mejor que las letras)."""
    head, bones = jolly.silhouette(box)
    rgba = np.zeros((box, box, 4), np.uint8)
    for alpha, col in ((bones, BONE), (head, WHITE)):
        a = (alpha * 255).astype(np.uint8)
        m = a > 0
        rgba[m, :3] = col
        rgba[..., 3] = np.maximum(rgba[..., 3], a)
    return Image.fromarray(rgba, "RGBA")


def icon(size, background=True, margin=None):
    ss = 4 if size >= 64 else 8
    n = size * ss
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    if background:
        m = int(n * (0.04 if margin is None else margin))
        s = n - 2 * m
        bg = vgrad(s, s, GRAPHITE, INK)
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.26), fill=255)
        out.paste(bg, (m, m), mask)
        if size >= 64:
            ImageDraw.Draw(out).rounded_rectangle([m, m, m + s - 1, m + s - 1], radius=int(s * 0.26), outline=(255, 255, 255, 26), width=max(1, n // 160))
        inner = int(s * (0.86 if size >= 64 else 0.94))
        off = m + (s - inner) // 2
    else:
        inner = int(n * 264 / 432)  # zona segura del ícono adaptativo
        off = (n - inner) // 2
    if size < 100:
        layer = solid_layer(inner, ss)
        out.alpha_composite(layer, (off, off))
    else:
        rows = rows_for(size)
        text = ascii_layer(jolly.text(jolly.cols_for(rows), rows), inner, inner, ss)
        out.paste(vgrad(inner, inner, WHITE, BONE), (off, off), text)
    return out.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    imgs = [icon(s) for s in sizes]
    imgs[-1].save(os.path.join(ROOT, "desktop/icon.ico"), format="ICO", sizes=[(s, s) for s in sizes], append_images=imgs[:-1])
    res = os.path.join(ROOT, "desktop/src/main/resources/icons")
    os.makedirs(res, exist_ok=True)
    for s, im in zip(sizes, imgs):
        if s in (16, 20, 24, 32, 40, 48, 64, 128, 256):
            im.save(os.path.join(res, f"flacker-{s}.png"))
    imgs[-1].save(os.path.join(ROOT, "shared/src/commonMain/composeResources/drawable/app_icon.png"))
    icon(432, background=False).save(os.path.join(ROOT, "app/src/main/res/drawable-nodpi/ic_launcher_fg_ascii.png"))
    # Hoja de muestra para revisarlos.
    sheet = Image.new("RGB", (sum(sizes) + 10 * len(sizes) + 10, 276), (200, 200, 205))
    x = 10
    for s, im in zip(sizes, imgs):
        sheet.paste(im, (x, 10), im); x += s + 10
    sheet.save(os.path.join(os.environ.get("TEMP", "."), "icons-sheet.png"))
    print("ok")
