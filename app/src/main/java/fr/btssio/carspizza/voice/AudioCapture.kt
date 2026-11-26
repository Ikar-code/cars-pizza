package fr.btssio.carspizza.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** F2 : micro externe s'il est branché, sinon micro de l'appareil. */
object MicSelector {
    // Priorité : micro USB, puis casque filaire. (Le Bluetooth demande une liaison SCO : non géré.)
    private val EXTERNAL_TYPES = listOf(
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
    )

    fun externalInput(context: Context): AudioDeviceInfo? {
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return EXTERNAL_TYPES.firstNotNullOfOrNull { type -> inputs.firstOrNull { it.type == type } }
    }

    fun label(device: AudioDeviceInfo?): String = when (device?.type) {
        null -> "micro de l'appareil"
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "micro externe USB"
        else -> "micro externe filaire"
    }
}

/**
 * Capture le micro en PCM 16 bits / 16 kHz / mono, par morceaux de 100 ms.
 * La source VOICE_COMMUNICATION active l'annulation d'écho quand l'appareil la propose :
 * utile car la voix du modèle sort par le haut-parleur pendant que le micro écoute.
 */
class AudioCapture(private val context: Context) {
    private var job: Job? = null

    /** Micro réellement utilisé, pour l'afficher à l'écran. */
    var micLabel: String = ""
        private set

    /**
     * Démarre la capture. La permission RECORD_AUDIO doit déjà être accordée.
     * @return null si tout va bien, sinon un message d'erreur à afficher.
     */
    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onChunk: (buffer: ByteArray, length: Int) -> Unit,
        onLevel: (Float) -> Unit,
    ): String? {
        val minBuffer = AudioRecord.getMinBufferSize(
            GeminiLive.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return "Micro indisponible sur cet appareil."

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                GeminiLive.INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CHUNK_BYTES * 4),
            )
        } catch (e: IllegalArgumentException) {
            return "Micro indisponible sur cet appareil."
        } catch (e: UnsupportedOperationException) {
            return "Autorisation du micro manquante."
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return "Impossible d'ouvrir le micro (utilisé par une autre application ?)."
        }

        val external = MicSelector.externalInput(context)
        if (external != null) record.setPreferredDevice(external)
        micLabel = MicSelector.label(external)

        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            record.release()
            return "Impossible de démarrer l'enregistrement."
        }

        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(CHUNK_BYTES)
            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        onLevel(level(buffer, read))
                        onChunk(buffer, read)
                    } else if (read < 0) {
                        break
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
        return null
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Volume du morceau, normalisé entre 0 et 1 (pour animer le bouton micro). */
    private fun level(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += sample.toDouble() * sample
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count) / 32768.0
        return (rms * 6.0).toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        const val CHUNK_BYTES = 3_200 // 100 ms de PCM 16 bits à 16 kHz
    }
}
