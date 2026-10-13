"""
Convierte la calavera vectorial (skull.py) en arte ASCII: cada celda toma un carácter según cuánto
la cubre la figura, con la rampa estilo jp2a " .,:;?iSk$". Impreso, se pega en Brand.kt (AsciiSkull).

    python tools/brand/ascii_skull.py 40 28
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image  # noqa: E402

import skull  # noqa: E402  (genera los trazos)

RAMP = " .,:;?iSk$"
_MASK = None


def _mask():
    global _MASK
    if _MASK is None:
        n = 1600
        s = n / 100
        m = Image.new("L", (n, n), 0)
        for b in skull.bones:
            m.paste(150, mask=skull.evenodd_mask(b, n, s))
        m.paste(255, mask=skull.evenodd_mask(skull.skull, n, s))
        m.paste(255, mask=skull.evenodd_mask(skull.eye, n, s))
        # Caja del dibujo: x 12..88, y 10..102.5
        _MASK = m.crop((int(11 * s), int(9 * s), int(89 * s), int(100 * s)))
    return _MASK


def ascii_skull(cols, rows):
    """Lista de renglones (sin espacios al final)."""
    box = _mask().resize((cols, rows), Image.BOX)
    lines = []
    for y in range(rows):
        row = ""
        for x in range(cols):
            v = box.getpixel((x, y)) / 255
            row += RAMP[min(len(RAMP) - 1, int(v ** 0.9 * (len(RAMP) - 1) + 0.35))]
        lines.append(row.rstrip())
    return lines


if __name__ == "__main__":
    cols = int(sys.argv[1]) if len(sys.argv) > 1 else 40
    rows = int(sys.argv[2]) if len(sys.argv) > 2 else 28
    print("\n".join(ascii_skull(cols, rows)))
