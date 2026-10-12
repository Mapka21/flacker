package com.marco.audiopro.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqPresetsTest {
    /** Géneros reales de la biblioteca → el preset que les toca. */
    @Test fun genreMapping() {
        val cases = mapOf(
            "Nu Metal" to "Rock pesado", "Heavy Metal" to "Rock pesado", "Hard rock" to "Rock pesado",
            "Emo Rap" to "Trap / Rap", "Cloud Rap" to "Trap / Rap", "Hip-Hop" to "Trap / Rap", "Trap" to "Trap / Rap",
            "Corrido Tumbado" to "Corridos", "Regional Mexicano" to "Corridos",
            "Alternative Rock" to "Rock", "Grunge" to "Rock", "Shoegaze" to "Rock", "Emo" to "Rock", "Pop rock" to "Rock",
            "Lo-Fi Hip Hop" to "Trap / Rap", "Lo-Fi" to "Lo-Fi / Chill", "Vaporwave" to "Lo-Fi / Chill", "Ambient" to "Lo-Fi / Chill",
            "Electronic" to "Electrónica", "Jazz" to "Jazz / Acústico", "Pop" to "Pop",
        )
        cases.forEach { (genre, preset) -> assertEquals(genre, preset, EqPresets.forGenre(genre).name) }
        assertEquals("Plano", EqPresets.forGenre(null).name)
    }

    /** Ningún preset sube más de 7 dB en ningún punto (con el pre-amp automático no satura). */
    @Test fun presetsStayTame() {
        EqPresets.builtIn.forEach { p ->
            assertEquals(p.name, 10, p.bands.size)
            val max = EqMath.maxResponseDb(p.bands)
            assertTrue("${p.name}: $max dB", max <= 7f)
        }
    }
}
