package fr.btssio.carspizza.voice

import android.util.Base64
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GeminiLive {
    /** Identifiant du modèle dans la doc Gemini API (« Gemini 3.8 Live »). */
    const val MODEL = "gemini-3.8-live"

    /** Audio envoyé : PCM 16 bits little-endian, 16 kHz, mono. Audio reçu : même format à 24 kHz. */
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000

    private const val ENDPOINT =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    fun url(apiKey: String) = "$ENDPOINT?key=$apiKey"

    // Connexion longue durée : pas de délai de lecture, un ping régulier détecte les coupures.
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }
}

/** Appel d'une fonction demandé par le modèle (ex. `add_pizzas`). */
data class ToolCall(val id: String, val name: String, val args: JSONObject)

/** Résultat renvoyé au modèle pour un [ToolCall]. */
data class ToolResult(val id: String, val name: String, val response: JSONObject)

/**
 * Session Gemini Live : un WebSocket bidirectionnel qui reçoit l'audio du micro et renvoie
 * les transcriptions, la voix du modèle et les appels de fonctions.
 *
 * Les callbacks du [Listener] arrivent sur un thread réseau d'OkHttp. Après [close], plus aucun
 * callback n'est émis.
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val systemPrompt: String,
    private val tools: JSONArray,
    private val listener: Listener,
) {
    interface Listener {
        /** La session est configurée : on peut envoyer l'audio. */
        fun onReady()
        /** Fragment de ce que l'employé vient de dire. */
        fun onInputTranscript(text: String)
        /** Fragment de ce que le modèle est en train de dire. */
        fun onOutputTranscript(text: String)
        /** Voix du modèle : PCM 16 bits, 24 kHz, mono. */
        fun onAudio(pcm: ByteArray)
        fun onToolCalls(calls: List<ToolCall>)
        /** L'employé a coupé la parole au modèle : il faut vider l'audio en attente. */
        fun onInterrupted()
        fun onTurnComplete()
        /** Fin de session. [error] est nul pour une fermeture normale. Appelé une seule fois. */
        fun onClosed(error: String?)
    }

    private val finished = AtomicBoolean(false)

    @Volatile private var socket: WebSocket? = null

    @Volatile private var ready = false

    fun connect() {
        val request = Request.Builder().url(GeminiLive.url(apiKey)).build()
        socket = GeminiLive.http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupMessage().toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

            // Le serveur envoie ses messages JSON en trames binaires : même traitement.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                finish(if (code == 1000) null else reason.ifBlank { "Session fermée par Gemini (code $code)." })
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(null)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                finish(describeFailure(t, response))
        })
    }

    /** Envoie un morceau d'audio du micro (PCM 16 bits, 16 kHz). Ignoré tant que la session n'est pas prête. */
    fun sendAudio(pcm: ByteArray, length: Int) {
        if (!ready || finished.get()) return
        val data = Base64.encodeToString(pcm, 0, length, Base64.NO_WRAP)
        socket?.send(
            """{"realtimeInput":{"audio":{"mimeType":"audio/pcm;rate=${GeminiLive.INPUT_SAMPLE_RATE}","data":"$data"}}}"""
        )
    }

    fun sendToolResponse(results: List<ToolResult>) {
        if (finished.get()) return
        val responses = JSONArray()
        results.forEach {
            responses.put(JSONObject().put("id", it.id).put("name", it.name).put("response", it.response))
        }
        socket?.send(
            JSONObject().put("toolResponse", JSONObject().put("functionResponses", responses)).toString()
        )
    }

    /** Fermeture voulue : aucun callback [Listener.onClosed] ne sera émis. */
    fun close() {
        finished.set(true)
        socket?.close(1000, null)
    }

    // ---- Protocole ---------------------------------------------------------------------------

    private fun setupMessage(): JSONObject = JSONObject().put(
        "setup",
        JSONObject()
            .put("model", "models/${GeminiLive.MODEL}")
            // Le modèle ne sait répondre qu'en audio ; le texte de ses réponses arrive par la transcription.
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO")))
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
            )
            .put("tools", tools)
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject()),
    )

    private fun handle(raw: String) {
        if (finished.get()) return
        val message = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            return
        }

        if (message.has("setupComplete")) {
            ready = true
            listener.onReady()
            return
        }

        message.optJSONObject("toolCall")?.let { toolCall ->
            val calls = parseToolCalls(toolCall.optJSONArray("functionCalls"))
            if (calls.isNotEmpty()) listener.onToolCalls(calls)
        }
        message.optJSONObject("serverContent")?.let { handleContent(it) }
    }

    private fun handleContent(content: JSONObject) {
        content.optJSONObject("inputTranscription")?.optString("text")
            ?.takeIf { it.isNotEmpty() }
            ?.let(listener::onInputTranscript)

        content.optJSONObject("outputTranscription")?.optString("text")
            ?.takeIf { it.isNotEmpty() }
            ?.let(listener::onOutputTranscript)

        content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val inline = parts.optJSONObject(i)?.let { it.optJSONObject("inlineData") ?: it.optJSONObject("inline_data") }
                val data = inline?.optString("data").orEmpty()
                if (data.isNotEmpty()) listener.onAudio(Base64.decode(data, Base64.DEFAULT))
            }
        }

        if (content.optBoolean("interrupted")) listener.onInterrupted()
        if (content.optBoolean("turnComplete")) listener.onTurnComplete()
    }

    private fun parseToolCalls(array: JSONArray?): List<ToolCall> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val call = array.optJSONObject(i) ?: return@mapNotNull null
            val name = call.optString("name")
            if (name.isEmpty()) null
            else ToolCall(call.optString("id"), name, call.optJSONObject("args") ?: JSONObject())
        }
    }

    private fun finish(error: String?) {
        if (finished.compareAndSet(false, true)) listener.onClosed(error)
    }

    private fun describeFailure(t: Throwable, response: Response?): String = when {
        response != null && response.code in listOf(400, 401, 403) ->
            "Clé API Gemini refusée (HTTP ${response.code}). Vérifiez-la dans les réglages."
        t is UnknownHostException || t is ConnectException || t is SocketTimeoutException ->
            "Pas de connexion à Internet : Gemini est injoignable."
        // La clé ne doit jamais apparaître dans un message affiché.
        else -> "Connexion à Gemini impossible : " +
            (t.message ?: t.javaClass.simpleName).replace(apiKey, "***")
    }
}
