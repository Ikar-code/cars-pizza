package fr.btssio.voxresto.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Joue la voix du modèle (PCM 16 bits, 24 kHz, mono). Les morceaux reçus sont mis en file d'attente
 * et écrits par une coroutine dédiée : l'écriture bloque, elle ne doit pas ralentir la lecture du réseau.
 * Une instance par session.
 */
class AudioPlayer {
    private val queue = Channel<ByteArray>(Channel.UNLIMITED)
    private var track: AudioTrack? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        val minBuffer = AudioTrack.getMinBufferSize(
            GeminiLive.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(GeminiLive.OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, GeminiLive.OUTPUT_SAMPLE_RATE * 2)) // ~1 s
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack.play()
        track = audioTrack
        job = scope.launch(Dispatchers.IO) {
            for (chunk in queue) audioTrack.write(chunk, 0, chunk.size)
        }
    }

    fun enqueue(pcm: ByteArray) {
        queue.trySend(pcm)
    }

    /** L'employé coupe la parole au modèle : on jette tout l'audio qui n'a pas encore été joué. */
    fun flush() {
        while (queue.tryReceive().isSuccess) {
            // vider la file
        }
        track?.let { runCatching { it.pause(); it.flush(); it.play() } }
    }

    fun stop() {
        job?.cancel()
        job = null
        track?.let {
            runCatching { it.pause(); it.flush() }
            it.release()
        }
        track = null
    }
}
