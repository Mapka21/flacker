package com.marco.audiopro.ui.viz

/**
 * Fondos estilo visualizador del Xbox 360 calculados pixel por pixel en la tarjeta de video.
 * El mismo código sirve como SkSL (PC) y AGSL (Android 13+). Todos reciben lo mismo:
 * iRes (tamaño), iTime (segundos), iFlow (tiempo que corre más rápido con la música),
 * iAudio = (graves, medios, agudos, golpe) y c0..c2, los colores vivos de la portada.
 */
internal object ShaderViz {
    private const val PRELUDE = """
uniform float2 iRes;
uniform float iTime;
uniform float iFlow;
uniform float4 iAudio;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;

float3 pal(float t) {
    float x = fract(t) * 3.0;
    if (x < 1.0) return mix(c0, c1, x);
    if (x < 2.0) return mix(c1, c2, x - 1.0);
    return mix(c2, c0, x - 2.0);
}
float2 rot(float2 p, float a) {
    float c = cos(a); float s = sin(a);
    return float2(c * p.x - s * p.y, s * p.x + c * p.y);
}
float3 tone(float3 c) { return c / (1.0 + c); }
"""

    /** Caleidoscopio: 8 espejos que doblan líneas de luz; se abre con el bajo. */
    private const val CALEIDO = """
half4 main(float2 fc) {
    float bass = iAudio.x; float mid = iAudio.y; float tre = iAudio.z; float beat = iAudio.w;
    float2 uv = (fc - 0.5 * iRes) / iRes.y;
    uv *= 1.0 - 0.22 * beat - 0.12 * bass;
    float r = length(uv);
    float seg = 6.2831853 / 8.0;
    float a = atan(uv.y, uv.x) + iFlow * 0.12 + iTime * 0.01;
    a = mod(a, seg);
    a = abs(a - seg * 0.5);
    float2 p = float2(cos(a), sin(a)) * r * (1.5 + 0.7 * bass);
    float3 col = float3(0.0);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        p = abs(p) - float2(0.42 + 0.12 * sin(iFlow * 0.31 + fi), 0.28 + 0.1 * cos(iFlow * 0.23));
        p = rot(p, 0.5 + 0.25 * sin(iFlow * 0.07));
        p *= 1.22;
        float d = abs(p.y) * (1.0 + fi * 0.6);
        col += pal(fi * 0.21 + iFlow * 0.04 + r * 0.6) * (0.010 + 0.012 * tre) / (d + 0.006);
    }
    col *= smoothstep(1.6, 0.1, r) * (0.75 + 0.5 * mid + 0.7 * bass);
    col += pal(r * 0.5 - iFlow * 0.03) * (0.12 + 0.1 * bass) * smoothstep(1.4, 0.0, r);
    col += pal(iFlow * 0.05) * beat * 0.6 * smoothstep(0.5, 0.0, r);
    return half4(half3(tone(col * 1.1)), 1.0);
}
"""

    /** Plasma: el clásico de los demos de los 90, con curvas de nivel de neón. */
    private const val PLASMA = """
half4 main(float2 fc) {
    float bass = iAudio.x; float mid = iAudio.y; float tre = iAudio.z; float beat = iAudio.w;
    float2 uv = (fc - 0.5 * iRes) / iRes.y * (2.6 - 1.1 * bass - 0.4 * beat);
    float t = iFlow * 0.6;
    float v = sin(uv.x * 2.1 + t) + sin(uv.y * 2.7 - t * 1.2) + sin((uv.x + uv.y) * 1.7 + t * 0.6)
        + sin(length(uv * 2.4 + float2(sin(t * 0.4), cos(t * 0.3))) * 2.0 - t * 1.6);
    v *= 0.25;
    float b = abs(fract(v * (3.0 + 2.0 * mid) - iTime * 0.12) - 0.5);
    float neon = 0.004 / (b * b + 0.004);
    float3 col = pal(v * 0.7 + t * 0.07) * (0.22 + 0.9 * neon * (0.85 + 0.6 * tre));
    col += float3(1.0) * pow(neon, 4.0) * (0.15 + 0.6 * beat);
    col *= 0.7 + 0.6 * bass + 0.5 * beat;
    return half4(half3(tone(col * 1.8)), 1.0);
}
"""

    /** Túnel: una rejilla de neón que te jala hacia adentro; los anillos destellan en cada golpe. */
    private const val TUNEL = """
half4 main(float2 fc) {
    float bass = iAudio.x; float mid = iAudio.y; float tre = iAudio.z; float beat = iAudio.w;
    float2 uv = (fc - 0.5 * iRes) / iRes.y;
    uv = rot(uv, iFlow * 0.08);
    uv += 0.07 * float2(sin(iFlow * 0.37), cos(iFlow * 0.29));
    float r = length(uv) + 0.0001;
    float a = atan(uv.y, uv.x);
    float z = (0.42 + 0.12 * bass) / r + iFlow * 1.4;
    float u = a / 6.2831853 * 12.0 + z * 0.3 * (0.5 + mid);
    float du = 0.5 - abs(fract(u) - 0.5);
    float dz = 0.5 - abs(fract(z) - 0.5);
    float d = min(du, dz);
    float lines = smoothstep(0.07, 0.0, d) + 0.012 / (d + 0.012);
    float3 col = pal(z * 0.15 + iTime * 0.01) * lines * (0.7 + 1.4 * bass);
    col += pal(z * 0.15 + 0.5) * smoothstep(0.12, 0.0, dz) * beat * 1.5;
    col *= clamp(r * 2.2, 0.0, 1.0);
    col += pal(iFlow * 0.03) * 0.035 / (r + 0.03) * (0.3 + tre);
    return half4(half3(tone(col)), 1.0);
}
"""

    /** Fractal: un conjunto "Kali" que se dobla sobre sí mismo como vidrio de colores. */
    private const val FRACTAL = """
half4 main(float2 fc) {
    float bass = iAudio.x; float mid = iAudio.y; float tre = iAudio.z; float beat = iAudio.w;
    float2 uv = (fc - 0.5 * iRes) / iRes.y;
    uv = rot(uv, iFlow * 0.04);
    float2 p = uv * (1.15 - 0.5 * bass - 0.15 * beat) + float2(0.15 * sin(iFlow * 0.05), 0.1 * cos(iFlow * 0.04));
    float2 k = float2(0.78 + 0.06 * sin(iFlow * 0.13), 0.62 + 0.06 * cos(iFlow * 0.09));
    float3 col = float3(0.0);
    float last = 0.0;
    for (int i = 0; i < 14; i++) {
        p = abs(p) / (dot(p, p) + 0.0001) - k;
        float m = dot(p, p);
        col += pal(float(i) * 0.07 + iFlow * 0.03 + iTime * 0.005) * exp(-abs(m - last) * 2.5) * 0.12;
        last = m;
    }
    col *= 0.85 + 0.5 * mid + 0.9 * bass + 0.6 * beat;
    col += float3(tre * 0.03);
    return half4(half3(tone(col * 2.0)), 1.0);
}
"""

    /** Estallido: rayos de luz desde el centro que crecen con el bajo y ondas que salen. */
    private const val ESTALLIDO = """
half4 main(float2 fc) {
    float bass = iAudio.x; float mid = iAudio.y; float tre = iAudio.z; float beat = iAudio.w;
    float2 uv = (fc - 0.5 * iRes) / iRes.y;
    float r = length(uv);
    float a = atan(uv.y, uv.x);
    float rays = pow(0.5 + 0.5 * sin(a * 9.0 + iFlow * 1.1), 18.0);
    rays += pow(0.5 + 0.5 * sin(a * 14.0 - iFlow * 1.7 + 1.3), 26.0) * 0.8;
    rays += pow(0.5 + 0.5 * sin(a * 5.0 + iFlow * 0.6 + sin(r * 6.0 - iFlow)), 10.0) * 0.6;
    rays *= exp(-r / (0.3 + 0.9 * bass + 0.5 * beat));
    float ring = 0.0;
    for (int i = 0; i < 3; i++) {
        float ph = fract(iTime * 0.35 + float(i) / 3.0);
        ring += smoothstep(0.02, 0.0, abs(r - ph * 1.4)) * (1.0 - ph);
    }
    float3 col = pal(a / 6.2831853 + iFlow * 0.05) * rays * (2.0 + tre);
    col += pal(r - iFlow * 0.1 + 0.33) * ring * (0.4 + 0.8 * mid);
    col += float3(1.0) * 0.05 / (r + 0.05) * (0.4 + beat);
    return half4(half3(tone(col)), 1.0);
}
"""

    /** Túnel del 360: dos texturas mezcladas que ruedan, curvas que viajan por el túnel, redondo o cuadrado. */
    private const val TUNNEL360 = """
uniform float4 iP0;
uniform float4 iP1;
uniform float4 iP2;
uniform float4 iP3;
float pat(float k, float2 p) {
    if (k < 0.5) { float2 g = floor(p); return mod(g.x + g.y, 2.0); }
    if (k < 1.5) { return 0.5 + 0.5 * sin(p.y * 6.2831853); }
    if (k < 2.5) { return 0.5 + 0.5 * sin(p.x * 12.566 + sin(p.y * 3.0) * 2.5); }
    if (k < 3.5) { float2 g = abs(fract(p) - 0.5); return smoothstep(0.40, 0.5, max(g.x, g.y)); }
    if (k < 4.5) { return fract(p.x + p.y * 0.5); }
    return 0.5 + 0.25 * sin(p.x * 3.1 + p.y * 1.7) + 0.25 * sin(p.y * 2.3 - p.x * 1.3);
}
half4 main(float2 fc) {
    float bass = iAudio.x; float mid = iAudio.y; float tre = iAudio.z; float beat = iAudio.w;
    float2 uv = (fc - 0.5 * iRes) / iRes.y;
    float r0 = length(uv);
    float d = clamp(r0 * 2.2, 0.0, 1.0);
    float t = d * 3.0;
    float bxv = t < 1.0 ? mix(iP2.x, iP2.y, t) : (t < 2.0 ? mix(iP2.y, iP2.z, t - 1.0) : mix(iP2.z, iP2.w, t - 2.0));
    float byv = t < 1.0 ? mix(iP3.x, iP3.y, t) : (t < 2.0 ? mix(iP3.y, iP3.z, t - 1.0) : mix(iP3.z, iP3.w, t - 2.0));
    uv -= float2(bxv, byv) * (1.0 - d) * 0.6;
    float r = iP0.w > 0.5 ? max(abs(uv.x), abs(uv.y)) : length(uv);
    r = max(r, 0.0005);
    float a = atan(uv.y, uv.x);
    float z = 0.38 / r + iP1.x;
    float u = (a / 6.2831853 + iP1.y) * iP1.w;
    float2 p = float2(u, z);
    float ta = pat(iP0.x, p);
    float tb = pat(iP0.y, p * float2(1.0, 0.5) + float2(iTime * 0.05, iFlow * 0.0));
    float t2 = mix(ta, tb, iP0.z);
    float3 col = c0 * t2 * t2 + c1 * pow(t2, 4.0) * (0.7 + bass);
    float edge = abs(fract(z) - 0.5);
    col += c2 * smoothstep(0.05, 0.0, abs(edge - 0.47)) * (0.8 + beat);
    col *= clamp(r * 3.2, 0.0, 1.0);
    col *= 0.75 + 0.5 * mid;
    col += float3(iP1.z);
    col += c1 * 0.05 / (r + 0.05) * tre;
    return half4(half3(tone(col)), 1.0);
}
"""

    /** El túnel del 360 ya con lo común (lo usa [Neon360]). */
    val TUNNEL360_SOURCE: String get() = PRELUDE + TUNNEL360

    fun source(p: VisualPreset): String? = when (p) {
        VisualPreset.CALEIDO -> CALEIDO
        VisualPreset.PLASMA -> PLASMA
        VisualPreset.TUNEL -> TUNEL
        VisualPreset.FRACTAL -> FRACTAL
        VisualPreset.ESTALLIDO -> ESTALLIDO
        else -> null
    }?.let { PRELUDE + it }
}
