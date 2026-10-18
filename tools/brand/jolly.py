"""
La calavera pirata con huesos cruzados de la marca: la misma figura que JollyRoger.kt (distancias con
signo, cuadro x -0.92..0.92, y -0.86..1.06), en numpy. La usan make_icons.py y las pruebas visuales.
"""
import numpy as np

X0, X1, Y0, Y1 = -0.92, 0.92, -0.86, 1.06
ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"


def _ellipse(x, y, cx, cy, rx, ry, rot=0.0):
    dx, dy = x - cx, y - cy
    c, s = np.cos(rot), np.sin(rot)
    px, py = c * dx + s * dy, -s * dx + c * dy
    return (np.hypot(px / rx, py / ry) - 1) * min(rx, ry)


def _box(x, y, cx, cy, hx, hy, r):
    qx, qy = np.abs(x - cx) - hx + r, np.abs(y - cy) - hy + r
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def _segment(x, y, ax, ay, bx, by, r):
    pax, pay, bax, bay = x - ax, y - ay, bx - ax, by - ay
    h = np.clip((pax * bax + pay * bay) / (bax * bax + bay * bay), 0, 1)
    return np.hypot(pax - bax * h, pay - bay * h) - r


def _gap(x, y, top, bottom):
    f = np.mod((x + 0.27) / 0.0675, 1.0)
    return (y >= top) & (y <= bottom) & (np.abs(x) <= 0.27) & (f < 0.17)


def skull(x, y):
    d = np.minimum(np.minimum(_ellipse(x, y, 0, -0.30, 0.58, 0.50), _box(x, y, 0, 0.06, 0.42, 0.20, 0.16)), _box(x, y, 0, 0.275, 0.27, 0.045, 0.02))
    holes = np.minimum(np.minimum(_ellipse(x, y, -0.22, -0.05, 0.155, 0.13, 0.28), _ellipse(x, y, 0.22, -0.05, 0.155, 0.13, -0.28)), _ellipse(x, y, 0, 0.165, 0.05, 0.065))
    d = np.maximum(d, -holes)
    return np.where(_gap(x, y, 0.235, 0.32), np.maximum(d, 0.01), d)


def jaw(x, y, open_=0.0):
    jy = y - open_ * 0.17
    d = np.minimum(_box(x, jy, 0, 0.36, 0.26, 0.035, 0.02), _box(x, jy, 0, 0.45, 0.30, 0.08, 0.08))
    return np.where(_gap(x, jy, 0.325, 0.395), np.maximum(d, 0.01), d)


def bones(x, y):
    d = np.minimum(_segment(x, y, -0.80, 0.30, 0.80, 0.96, 0.062), _segment(x, y, 0.80, 0.30, -0.80, 0.96, 0.062))
    for ex, ey, dx, dy in [(-0.80, 0.30, -1, -0.82), (0.80, 0.96, 1, 0.82), (0.80, 0.30, 1, -0.82), (-0.80, 0.96, -1, 0.82)]:
        l = np.hypot(dx, dy); ux, uy = dx / l, dy / l; nx, ny = -uy, ux
        for sgn in (1, -1):
            d = np.minimum(d, np.hypot(x - (ex + sgn * nx * 0.065 + ux * 0.01), y - (ey + sgn * ny * 0.065 + uy * 0.01)) - 0.08)
    return d


def fields(x, y):
    """(cabeza, huesos visibles) como distancias."""
    head = np.minimum(skull(x, y), jaw(x, y))
    b = bones(x, y)
    b = np.where(head < 0.05, np.maximum(b, 0.05 - head), b)
    return head, b


def silhouette(px):
    """Alfa (0..1) de la calavera y de los huesos en un cuadro de px × px."""
    span = max(X1 - X0, Y1 - Y0)
    ox = X0 - (span - (X1 - X0)) / 2; oy = Y0 - (span - (Y1 - Y0)) / 2
    g = (np.arange(px) + 0.5) * span / px
    x, y = np.meshgrid(ox + g, oy + g)
    head, b = fields(x, y)
    aa = 1.2 * span / px
    return np.clip(0.5 - head / aa, 0, 1), np.clip(0.5 - b / aa, 0, 1)


def text(cols, rows, seed=333):
    """La figura hecha de letras al azar (como JollyRoger.text)."""
    rnd = np.random.default_rng(seed)
    cw, ch = (X1 - X0) / cols, (Y1 - Y0) / rows
    sub = (np.arange(3) + 0.5) / 3
    lines = []
    for r in range(rows):
        xs = X0 + (np.arange(cols)[:, None] + sub[None, :]) * cw
        ys = Y0 + (r + sub) * ch
        X, Y = np.meshgrid(xs.ravel(), ys)
        head, b = fields(X, Y)
        hit = ((head < 0) | (b < 0)).reshape(3, cols, 3).sum(axis=(0, 2))
        row = "".join(ALNUM[rnd.integers(len(ALNUM))] if h >= 5 else (".:;il"[rnd.integers(5)] if h >= 3 else " ") for h in hit)
        lines.append(row.rstrip())
    return lines


def cols_for(rows):
    return int(rows * 1.7 * (X1 - X0) / (Y1 - Y0))


if __name__ == "__main__":
    print("\n".join(text(cols_for(28), 28)))
