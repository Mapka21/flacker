package com.marco.audiopro.data.info

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.marco.audiopro.data.subsonic.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reseña de álbum generada con Claude cuando Last.fm/Navidrome no tienen nada.
 * Se llama una sola vez por álbum: el resultado se cachea en disco (AlbumInfoRepository).
 */
class ClaudeReviewer {

    private val system = """
        Eres un crítico musical melómano que escribe para una app personal de audio de alta fidelidad.
        Escribe en español, en tono cercano pero informado, 2 o 3 párrafos cortos (máx. ~180 palabras):
        contexto del álbum, cómo suena (producción, instrumentación, mezcla) y pistas destacadas.
        Sin títulos, sin listas, sin markdown.
        Si no conoces este álbum con certeza, no inventes datos: dilo en una frase y básate solo en
        los metadatos que te doy (género, año, duración, títulos de las canciones).
    """.trimIndent()

    suspend fun review(apiKey: String, album: Album, tags: List<String>): String = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            val tracks = album.song.joinToString("\n") { s ->
                val d = s.duration ?: 0
                "${s.discNumber?.let { "$it-" } ?: ""}${s.track ?: "?"}. ${s.title} (${d / 60}:${"%02d".format(d % 60)})"
            }
            val meta = buildString {
                appendLine("Álbum: ${album.name}")
                appendLine("Artista: ${album.artist ?: "desconocido"}")
                album.year?.let { appendLine("Año: $it") }
                val genres = album.genres?.map { it.name }?.filter { it.isNotBlank() }.orEmpty().ifEmpty { listOfNotNull(album.genre) }
                if (genres.isNotEmpty()) appendLine("Géneros: ${genres.joinToString()}")
                album.recordLabels?.firstOrNull()?.name?.takeIf { it.isNotBlank() }?.let { appendLine("Sello: $it") }
                if (tags.isNotEmpty()) appendLine("Tags de Last.fm: ${tags.joinToString()}")
                appendLine("Pistas:")
                append(tracks)
            }
            val params = MessageCreateParams.builder()
                .model("claude-opus-5-5")
                .maxTokens(16000L)
                .system(system)
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                // Si el modelo rechaza la petición, el servidor reintenta con otro modelo adecuado.
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage(meta)
                .build()
            val msg = client.beta().messages().create(params)
            if (msg.stopReason().orElse(null) == BetaStopReason.REFUSAL) {
                throw IllegalStateException("Claude no generó la reseña")
            }
            msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n").trim()
                .ifEmpty { throw IllegalStateException("Respuesta vacía de Claude") }
        } finally {
            client.close()
        }
    }
}
