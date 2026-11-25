package fr.btssio.voxresto.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Synthèse vocale (réponses de l'assistant à voix haute, en français). */
class SpeechOutput(context: Context) {
    private var ready = false
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale.FRANCE)
                ready = result != null &&
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(text: String) {
        if (ready) engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vox-${System.nanoTime()}")
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
