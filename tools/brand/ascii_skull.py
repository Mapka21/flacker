"""
Convierte la calavera vectorial (skull.py) en arte ASCII: cada celda toma un carácter según cuánto
la cubre la figura, con la rampa estilo jp2a de la referencia (. , : ? i S $). Imprime el texto
para pegarlo en Brand.kt (AsciiSkull).

    python tools/brand/ascii_skull.py 34 17
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image  # noqa: E402

import skull  # noqa: E402  (genera los trazos; también escribe la vista previa)

COLS = int(sys.argv[1]) if len(sys.argv) > 1 else 34
ROWS = int(sys.argv[2]) if len(sys.argv) > 2 else 17
RAMP = " .,:;?iSk$"

N = 1600
s = N / 100
mask = Image.new("L", (N, N), 0)
for b in skull.bones:
    mask.paste(150, mask=skull.evenodd_mask(b, N, s))
mask.paste(255, mask=skull.evenodd_mask(skull.skull, N, s))
mask.paste(255, mask=skull.evenodd_mask(skull.eye, N, s))
# Caja del dibujo: x 12..88, y 10..102.5
x0, y0, x1, y1 = int(11 * s), int(9 * s), int(89 * s), int(min(100, 103) * s)
box = mask.crop((x0, y0, x1, y1)).resize((COLS, ROWS), Image.BOX)
lines = []
for y in range(ROWS):
    row = ""
    for x in range(COLS):
        v = box.getpixel((x, y)) / 255
        row += RAMP[min(len(RAMP) - 1, int(v ** 0.9 * (len(RAMP) - 1) + 0.35))]
    lines.append(row.rstrip())
print("\n".join(lines))
