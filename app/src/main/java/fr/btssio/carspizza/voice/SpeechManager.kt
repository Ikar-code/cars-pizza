package fr.btssio.carspizza.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Enveloppe autour de [SpeechRecognizer] (reconnaissance vocale native Android, français).
 * À utiliser depuis le thread principal uniquement (contrainte de l'API Android).
 */
class SpeechManager(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onListening()
        /** Niveau sonore normalisé entre 0 et 1, pour animer le bouton micro. */
        fun onLevel(level: Float)
        fun onPartial(text: String)
        fun onProcessing()
        /** Hypothèses de reconnaissance, de la plus probable à la moins probable. */
        fun onResults(alternatives: List<String>)
        fun onError(message: String)
    }

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable) {
            callbacks.onError("La reconnaissance vocale n'est pas disponible sur cet appareil.")
            return
        }
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        engine.cancel()
        engine.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
        )
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = callbacks.onListening()

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB varie en pratique d'environ -2 à 10.
            callbacks.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = callbacks.onProcessing()

        override fun onError(error: Int) {
            callbacks.onError(describe(error))
        }

        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (texts.isEmpty()) callbacks.onError(describe(SpeechRecognizer.ERROR_NO_MATCH))
            else callbacks.onResults(texts)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let(callbacks::onPartial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun describe(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Je n'ai rien entendu, réessayez."
        SpeechRecognizer.ERROR_AUDIO -> "Problème d'accès au micro."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Autorisation du micro manquante."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Réseau indisponible pour la reconnaissance vocale."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Le moteur vocal est occupé, réessayez."
        SpeechRecognizer.ERROR_SERVER -> "Le service vocal a rencontré une erreur."
        else -> "Erreur de reconnaissance vocale (code $code)."
    }
}
