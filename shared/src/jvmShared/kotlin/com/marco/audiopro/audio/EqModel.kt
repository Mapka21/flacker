package com.marco.audiopro.audio

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType(val label: String, val short: String, val hasGain: Boolean) {
    PEAK("Peaking", "PK", true),
    LOW_SHELF("Low shelf", "LS", true),
    HIGH_SHELF("High shelf", "HS", true),
    LOW_PASS("Low pass", "LP", false),
    HIGH_PASS("High pass", "HP", false),
}

@Serializable
data class EqBand(
    val type: FilterType = FilterType.PEAK,
    val freq: Float,
    val gainDb: Float = 0f,
    val q: Float = 1f,
    val enabled: Boolean = true,
)

@Serializable
data class EqConfig(
    val enabled: Boolean = true,
    val preampDb: Float = 0f,
    val autoPreamp: Boolean = true,
    val dither: Boolean = true,
    val presetName: String = "Plano",
    val bands: List<EqBand> = EqPresets.flatBands(),
    /** Cambia solo de preset según el género de cada canción ([EqPresets.forGenre]). */
    val autoGenre: Boolean = false,
    /** Volumen parejo (nivelador de sonoridad); funciona aunque el EQ esté apagado. */
    val leveler: LevelMode = LevelMode.OFF,
) {
    /** Pre-amp efectivo: con auto, baja lo justo para que el pico de la curva no pase de 0 dB. */
    fun effectivePreampDb(): Float =
        // `+ 0f` convierte -0.0 en 0.0 (si no, se muestra "-0.0 dB").
        (if (autoPreamp) -max(0f, EqMath.maxResponseDb(bands)) else preampDb) + 0f
}

@Serializable
data class EqPreset(val name: String, val bands: List<EqBand>)

/** Coeficientes normalizados (a0 = 1): b0, b1, b2, a1, a2. RBJ Audio EQ Cookbook. */
object EqMath {
    fun coefficients(b: EqBand, sampleRate: Int): DoubleArray {
        val fs = sampleRate.toDouble()
        val f = b.freq.toDouble().coerceIn(10.0, fs * 0.49)
        val q = b.q.toDouble().coerceIn(0.1, 20.0)
        val w0 = 2 * PI * f / fs
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        val a = 10.0.pow(b.gainDb / 40.0)
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        when (b.type) {
            FilterType.PEAK -> {
                b0 = 1 + alpha * a; b1 = -2 * cw; b2 = 1 - alpha * a
                a0 = 1 + alpha / a; a1 = -2 * cw; a2 = 1 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) - (a - 1) * cw + s)
                b1 = 2 * a * ((a - 1) - (a + 1) * cw)
                b2 = a * ((a + 1) - (a - 1) * cw - s)
                a0 = (a + 1) + (a - 1) * cw + s
                a1 = -2 * ((a - 1) + (a + 1) * cw)
                a2 = (a + 1) + (a - 1) * cw - s
            }
            FilterType.HIGH_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) + (a - 1) * cw + s)
                b1 = -2 * a * ((a - 1) + (a + 1) * cw)
                b2 = a * ((a + 1) + (a - 1) * cw - s)
                a0 = (a + 1) - (a - 1) * cw + s
                a1 = 2 * ((a - 1) - (a + 1) * cw)
                a2 = (a + 1) - (a - 1) * cw - s
            }
            FilterType.LOW_PASS -> {
                b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha
            }
        }
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    /** Magnitud en dB de un biquad en la frecuencia f. */
    fun magnitudeDb(c: DoubleArray, f: Double, fs: Double): Double {
        val w = 2 * PI * f / fs
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = c[0] + c[1] * c1 + c[2] * c2
        val ni = -(c[1] * s1 + c[2] * s2)
        val dr = 1 + c[3] * c1 + c[4] * c2
        val di = -(c[3] * s1 + c[4] * s2)
        val mag2 = (nr * nr + ni * ni) / (dr * dr + di * di)
        return 10 * log10(mag2.coerceAtLeast(1e-20))
    }

    /** Respuesta total (suma en dB de las bandas activas) en `points` frecuencias log entre 20 Hz y 20 kHz. */
    fun response(bands: List<EqBand>, points: Int, fs: Double = 48000.0): FloatArray {
        val coefs = bands.filter { it.enabled }.map { coefficients(it, fs.toInt()) }
        return FloatArray(points) { i ->
            val f = freqAt(i / (points - 1f))
            coefs.sumOf { magnitudeDb(it, f.toDouble(), fs) }.toFloat()
        }
    }

    fun maxResponseDb(bands: List<EqBand>): Float = response(bands, 256).maxOrNull() ?: 0f

    /** x en [0,1] → frecuencia log 20 Hz..20 kHz. */
    fun freqAt(x: Float): Float = 20f * 1000f.pow(x)
    fun xOf(freq: Float): Float = (log10(freq / 20f) / 3f).coerceIn(0f, 1f)
}

object EqPresets {
    val centers = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun flatBands(): List<EqBand> = centers.mapIndexed { i, f ->
        when (i) {
            0 -> EqBand(FilterType.LOW_SHELF, f, 0f, 0.71f)
            centers.lastIndex -> EqBand(FilterType.HIGH_SHELF, f, 0f, 0.71f)
            else -> EqBand(FilterType.PEAK, f, 0f, 1.0f)
        }
    }

    private fun gains(vararg g: Float): List<EqBand> =
        flatBands().mapIndexed { i, b -> b.copy(gainDb = g.getOrElse(i) { 0f }) }

    private fun hp(f: Float) = EqBand(FilterType.HIGH_PASS, f, 0f, 0.71f)
    private fun ls(f: Float, g: Float) = EqBand(FilterType.LOW_SHELF, f, g, 0.71f)
    private fun hs(f: Float, g: Float) = EqBand(FilterType.HIGH_SHELF, f, g, 0.71f)
    private fun pk(f: Float, g: Float, q: Float = 1f) = EqBand(FilterType.PEAK, f, g, q)

    /*
     * Presets de "mezcla": bandas paramétricas puestas donde está cada instrumento, pensadas para lo
     * que más hay en la biblioteca (rock alternativo, metal/nu metal, rap/trap, corridos, pop, lo-fi).
     * Quitan lodo (200–400 Hz) en vez de sólo subir todo, y el pre-amp automático evita saturar.
     */

    /** Metal, nu metal, hard rock: bombo con golpe, sin lodo, guitarras con mordida pero sin "fizz". */
    val heavyRock = EqPreset(
        "Rock pesado",
        listOf(
            hp(28f), ls(85f, 3f), pk(110f, 1.5f, 1.2f), pk(300f, -3f, 1.1f), pk(800f, -1.5f),
            pk(1800f, 1f), pk(3200f, 2.5f, 0.9f), pk(7000f, -1.5f, 2.5f), pk(10000f, 1f), hs(13000f, 2f),
        ),
    )

    /** Rock, alternativo, indie, grunge, shoegaze: cuerpo, guitarras al frente y aire arriba. */
    val rock = EqPreset(
        "Rock",
        listOf(
            hp(25f), pk(60f, 1f, 1.4f), ls(90f, 2.5f), pk(200f, -1.5f, 1.2f), pk(500f, -0.5f),
            pk(1200f, 1f), pk(2800f, 2f), pk(5000f, 1f, 1.2f), pk(8000f, 0.5f), hs(11000f, 2.5f),
        ),
    )

    /** Pop: graves redondos, voz presente, sibilancias domadas y brillo "de radio". */
    val pop = EqPreset(
        "Pop",
        listOf(
            hp(22f), ls(70f, 2.5f), pk(120f, 1f, 1.2f), pk(300f, -2f, 1.2f), pk(700f, -0.5f),
            pk(2000f, 1f), pk(3500f, 2f), pk(6500f, -1f, 3f), pk(9000f, 1f), hs(13000f, 3f),
        ),
    )

    /** Trap, rap, cloud rap, emo rap: el 808 grande abajo, voz clara y hi-hats nítidos. */
    val trap = EqPreset(
        "Trap / Rap",
        listOf(
            hp(20f), ls(50f, 5f), pk(90f, 1.5f, 1.4f), pk(250f, -2.5f, 1.1f), pk(500f, -1f),
            pk(1500f, 0.5f), pk(3000f, 2f), pk(6000f, -1f, 3f), pk(9000f, 1f), hs(12000f, 2.5f),
        ),
    )

    /** Corridos y regional: el tololoche presente, requinto brillante y la voz al frente. */
    val corridos = EqPreset(
        "Corridos",
        listOf(
            hp(30f), ls(75f, 2.5f), pk(140f, 1f, 1.3f), pk(300f, -1.5f, 1.2f), pk(800f, -0.5f),
            pk(2200f, 2f), pk(3500f, 1f, 1.2f), pk(5500f, 1.5f, 1.2f), pk(8000f, 0f), hs(11000f, 1.5f),
        ),
    )

    /** Batería al frente: bombo, cuerpo y chasquido de tarola, platillos; se quita la caja. */
    val drums = EqPreset(
        "Batería al frente",
        listOf(
            hp(25f), pk(60f, 3f, 1.4f), pk(200f, 1.5f, 1.6f), pk(400f, -2f, 1.2f), pk(900f, -1.5f),
            pk(2000f, 0f), pk(4500f, 2.5f, 1.2f), pk(7000f, 0f), pk(9500f, 1.5f), hs(12000f, 2.5f),
        ),
    )

    /** Guitarras al frente: medios-altos con mordida, sin lodo ni "fizz" de distorsión. */
    val guitars = EqPreset(
        "Guitarras al frente",
        listOf(
            hp(40f), ls(100f, -1f), pk(250f, -2f, 1.2f), pk(600f, 0f), pk(1200f, 1.5f),
            pk(2600f, 3f, 0.8f), pk(4000f, 1f), pk(6500f, -1.5f, 2.5f), pk(9000f, 0.5f), hs(12000f, 1.5f),
        ),
    )

    /** Lo-fi, ambient, vaporwave, downtempo: tibio y suave, sin agudos que piquen. */
    val lofi = EqPreset(
        "Lo-Fi / Chill",
        listOf(
            hp(25f), ls(100f, 3f), pk(200f, 1f), pk(500f, 0f), pk(1000f, 0f),
            pk(2500f, -1f), pk(4500f, -1f), pk(7000f, 0f), pk(9000f, 0f), hs(10000f, 1f),
        ),
    )

    val builtIn: List<EqPreset> = listOf(
        EqPreset("Plano", flatBands()),
        heavyRock, rock, pop, trap, corridos, drums, guitars, lofi,
        EqPreset("Electrónica", gains(5f, 4f, 1f, 0f, -2f, 0f, 1f, 1f, 3f, 4f)),
        EqPreset("Jazz / Acústico", gains(2f, 2f, 1f, 1f, -1f, -1f, 0f, 1f, 2f, 2f)),
        EqPreset("Bass boost", gains(6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqPreset("V-Shape", gains(5f, 4f, 2f, 0f, -1f, -2f, -1f, 2f, 4f, 5f)),
        EqPreset("Cálido", gains(2f, 3f, 2f, 1f, 0f, 0f, -1f, -1f, -2f, -2f)),
        EqPreset("Vocal", gains(-1f, -1f, -2f, -1f, 1f, 2f, 3f, 2f, 0f, -1f)),
        EqPreset("Brillante", gains(0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f)),
        EqPreset("Loudness", gains(6f, 4f, 1f, 0f, 0f, 0f, 0f, 1f, 2f, 5f)),
    )

    /**
     * El preset que mejor le queda a un género (para "Auto por género"). El orden importa: "emo rap"
     * es rap aunque diga emo, "pop rock" es rock, "nu metal" es pesado.
     */
    fun forGenre(genre: String?): EqPreset {
        val g = genre?.lowercase() ?: return builtIn.first()
        fun has(vararg w: String) = w.any { it in g }
        return when {
            has("metal", "hard rock", "hardcore", "heavy", "djent", "thrash", "grindcore") -> heavyRock
            has("rap", "hip hop", "hip-hop", "trap", "drill", "phonk", "grime") -> trap
            has("corrido", "regional", "banda", "norteñ", "sierreñ", "mariachi", "ranchera", "cumbia", "tumbado") -> corridos
            has("rock", "grunge", "punk", "alternative", "alternativ", "indie", "shoegaze", "emo", "post-") -> rock
            has("lo-fi", "lofi", "ambient", "downtempo", "chill", "vaporwave", "dream") -> lofi
            has("electr", "house", "techno", "edm", "dubstep", "synth", "trance", "drum and bass", "dnb") -> builtIn.first { it.name == "Electrónica" }
            has("jazz", "acoustic", "acústic", "folk", "classical", "clásic", "blues", "soul") -> builtIn.first { it.name == "Jazz / Acústico" }
            has("pop", "r&b", "rnb", "reggaeton", "latin") -> pop
            else -> builtIn.first()
        }
    }
}
