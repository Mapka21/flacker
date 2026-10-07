package com.marco.audiopro.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Adaptador de [EqDsp] (compartido con la PC) a la cadena de audio de ExoPlayer.
 * Con el EQ apagado copia las muestras sin tocarlas.
 */
class ParametricEqProcessor(private val dsp: EqDsp) : BaseAudioProcessor() {
    private var channels = 2
    private var isFloat = false
    private var buf = FloatArray(0)
    private val dither = Dither()

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        dsp.inputFormat = PcmFormat(
            inputAudioFormat.sampleRate, inputAudioFormat.channelCount,
            if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) "32-bit float" else "16-bit",
        )
        return inputAudioFormat
    }

    override fun onFlush() {
        channels = inputAudioFormat.channelCount
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        dsp.reset(inputAudioFormat.sampleRate, channels)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        if (!dsp.isActive) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val samples = size / if (isFloat) 4 else 2
        if (buf.size < samples) buf = FloatArray(samples)
        for (i in 0 until samples) {
            buf[i] = if (isFloat) inputBuffer.getFloat() else inputBuffer.getShort() / 32768f
        }
        inputBuffer.position(inputBuffer.limit())
        dsp.process(buf, samples / channels)
        val ditherOn = dsp.dither
        for (i in 0 until samples) {
            if (isFloat) out.putFloat(buf[i]) else out.putShort(dither.quantize(buf[i], 16, ditherOn).toShort())
        }
        out.flip()
    }

    override fun onReset() {
        dsp.inputFormat = null
    }
}
