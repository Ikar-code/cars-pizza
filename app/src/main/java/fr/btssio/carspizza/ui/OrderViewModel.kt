package fr.btssio.carspizza.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.btssio.carspizza.CarsPizzaApp
import fr.btssio.carspizza.data.ConfirmResult
import fr.btssio.carspizza.data.DraftOrder
import fr.btssio.carspizza.data.Pizza
import fr.btssio.carspizza.voice.AudioCapture
import fr.btssio.carspizza.voice.AudioPlayer
import fr.btssio.carspizza.voice.CommandParser
import fr.btssio.carspizza.voice.GeminiLiveClient
import fr.btssio.carspizza.voice.MenuEntry
import fr.btssio.carspizza.voice.MenuMatch
import fr.btssio.carspizza.voice.OrderTools
import fr.btssio.carspizza.voice.SpeechManager
import fr.btssio.carspizza.voice.SpeechOutput
import fr.btssio.carspizza.voice.ToolCall
import fr.btssio.carspizza.voice.ToolResult
import fr.btssio.carspizza.voice.VoiceCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class VoiceState { IDLE, CONNECTING, LISTENING, PROCESSING }

/** Moteur vocal utilisé : Gemini Live (clé API requise) ou la reconnaissance vocale d'Android. */
enum class VoiceEngine { GEMINI, ANDROID }

data class ChatLine(val id: Long, val fromUser: Boolean, val text: String)

data class OrderUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val engine: VoiceEngine = VoiceEngine.ANDROID,
    val level: Float = 0f,
    val partialText: String = "",
    val transcript: List<ChatLine> = emptyList(),
    val error: String? = null,
    val speakReplies: Boolean = true,
    val speechAvailable: Boolean = true,
    val micLabel: String = "",
    val geminiConfigured: Boolean = false,
    val useGemini: Boolean = true,
) {
    val active: Boolean
        get() = voiceState != VoiceState.IDLE
}

class OrderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as CarsPizzaApp).repository
    private val settings = (app as CarsPizzaApp).settings
    private val tts = SpeechOutput(app)
    private var nextLineId = 1L

    private val _ui = MutableStateFlow(
        OrderUiState(
            transcript = listOf(
                ChatLine(0, false, "Bonjour ! Appuyez sur le micro et dites par exemple : « deux margherita et une reine ».")
            ),
            geminiConfigured = settings.geminiApiKey.isNotBlank(),
            useGemini = settings.useGemini,
        )
    )
    val uiState: StateFlow<OrderUiState> = _ui.asStateFlow()

    /** Commande en cours de dictée (en mémoire tant qu'elle n'est pas validée). */
    val draft: StateFlow<DraftOrder> = repo.draft

    /** Pizzas disponibles, pour la saisie manuelle (F8). */
    val availablePizzas: StateFlow<List<Pizza>> =
        repo.pizzas
            .map { list -> list.filter { it.isAvailable } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Moteur Android (repli) --------------------------------------------------------------

    private val androidCallbacks = object : SpeechManager.Callbacks {
        override fun onListening() = _ui.update {
            it.copy(voiceState = VoiceState.LISTENING, partialText = "", error = null)
        }

        override fun onLevel(level: Float) = _ui.update { it.copy(level = level) }

        override fun onPartial(text: String) = _ui.update { it.copy(partialText = text) }

        override fun onProcessing() = _ui.update { it.copy(voiceState = VoiceState.PROCESSING, level = 0f) }

        override fun onResults(alternatives: List<String>) {
            _ui.update { it.copy(voiceState = VoiceState.IDLE, partialText = "", level = 0f) }
            handleUtterance(alternatives)
        }

        override fun onError(message: String) = _ui.update {
            it.copy(voiceState = VoiceState.IDLE, partialText = "", level = 0f, error = message)
        }
    }

    private val speech = SpeechManager(app, androidCallbacks)

    // ---- Session Gemini Live -----------------------------------------------------------------

    @Volatile private var client: GeminiLiveClient? = null
    @Volatile private var player: AudioPlayer? = null
    private var capture: AudioCapture? = null
    private var idleJob: Job? = null

    /** Identifie la session en cours : les callbacks d'une session terminée sont ignorés. */
    private var sessionId = 0
    private val inputBuffer = StringBuilder()
    private val outputBuffer = StringBuilder()

    init {
        _ui.update { it.copy(speechAvailable = speech.isAvailable || it.geminiConfigured) }
    }

    // ---- Actions de l'écran ------------------------------------------------------------------

    fun startListening() {
        if (_ui.value.active) return
        tts.stop()
        _ui.update { it.copy(error = null) }
        val key = settings.geminiApiKey
        if (settings.useGemini && key.isNotBlank()) startGemini(key) else startAndroidSpeech()
    }

    fun stopListening() {
        when {
            client != null || _ui.value.voiceState == VoiceState.CONNECTING -> endSession(null)
            else -> speech.stop()
        }
    }

    fun onPermissionDenied() {
        _ui.update { it.copy(error = "Autorisation du micro refusée : vous pouvez écrire ou ajouter les pizzas à la main.") }
    }

    fun toggleSpeakReplies() {
        val enabled = !_ui.value.speakReplies
        if (!enabled) {
            tts.stop()
            player?.flush()
        }
        _ui.update { it.copy(speakReplies = enabled) }
    }

    /** Une clé vide conserve la clé actuelle (elle n'est jamais réaffichée). */
    fun saveSettings(apiKey: String, useGemini: Boolean) {
        if (apiKey.isNotBlank()) settings.geminiApiKey = apiKey
        settings.useGemini = useGemini
        _ui.update {
            it.copy(
                geminiConfigured = settings.geminiApiKey.isNotBlank(),
                useGemini = useGemini,
                speechAvailable = speech.isAvailable || settings.geminiApiKey.isNotBlank(),
            )
        }
    }

    fun submitText(text: String) {
        if (text.isBlank()) return
        handleUtterance(listOf(text))
    }

    /** F8 : ajout manuel d'une pizza (échec de la reconnaissance vocale). */
    fun addPizza(pizzaId: Long) {
        viewModelScope.launch { repo.addToDraft(pizzaId, 1) }
    }

    fun increment(pizzaId: Long) {
        viewModelScope.launch { repo.addToDraft(pizzaId, 1) }
    }

    fun decrement(pizzaId: Long) {
        viewModelScope.launch { repo.removeFromDraft(pizzaId, 1) }
    }

    fun confirm() {
        viewModelScope.launch { say(executeLocal(listOf(VoiceCommand.Confirm))) }
    }

    fun cancel() {
        viewModelScope.launch { say(executeLocal(listOf(VoiceCommand.CancelOrder))) }
    }

    // ---- Gemini Live -------------------------------------------------------------------------

    private fun startGemini(key: String) {
        val id = ++sessionId
        inputBuffer.clear()
        outputBuffer.clear()
        _ui.update { it.copy(voiceState = VoiceState.CONNECTING, engine = VoiceEngine.GEMINI, partialText = "") }
        viewModelScope.launch {
            // Le menu actuel (pizzas disponibles, prix) est envoyé au modèle à chaque session.
            val menu = repo.availablePizzas()
            if (id != sessionId) return@launch
            val prompt = OrderTools.systemPrompt(menu.map { "- ${it.name} (${Format.price(it.priceCents)})" })
            val newClient = GeminiLiveClient(key, prompt, OrderTools.declarations(), liveListener(id))
            val newPlayer = AudioPlayer().also { it.start(viewModelScope) }
            client = newClient
            player = newPlayer
            newClient.connect()
        }
    }

    private fun liveListener(id: Int) = object : GeminiLiveClient.Listener {
        // Les callbacks arrivent sur un thread réseau : on les rejoue dans l'ordre sur le thread principal.
        private fun post(block: suspend () -> Unit) {
            viewModelScope.launch { if (id == sessionId) block() }
        }

        override fun onReady() = post {
            val live = client ?: return@post
            val mic = AudioCapture(getApplication())
            val failure = mic.start(
                scope = viewModelScope,
                onChunk = { buffer, length -> live.sendAudio(buffer, length) },
                onLevel = { level -> _ui.update { it.copy(level = level) } },
            )
            if (failure != null) {
                endSession(failure)
                return@post
            }
            capture = mic
            _ui.update { it.copy(voiceState = VoiceState.LISTENING, micLabel = mic.micLabel, error = null) }
            resetIdleTimer()
        }

        override fun onInputTranscript(text: String) = post {
            inputBuffer.append(text)
            _ui.update { it.copy(partialText = inputBuffer.toString().trim()) }
            resetIdleTimer()
        }

        override fun onOutputTranscript(text: String) = post {
            flushInput()
            outputBuffer.append(text)
        }

        override fun onAudio(pcm: ByteArray) {
            if (id == sessionId && _ui.value.speakReplies) player?.enqueue(pcm)
        }

        override fun onToolCalls(calls: List<ToolCall>) = post {
            flushInput()
            val results = calls.map { ToolResult(it.id, it.name, runTool(it)) }
            if (id == sessionId) client?.sendToolResponse(results)
            resetIdleTimer()
        }

        override fun onInterrupted() {
            // L'employé parle par-dessus le modèle : on coupe tout de suite ce qui n'est pas encore joué.
            player?.flush()
            post { flushOutput() }
        }

        override fun onTurnComplete() = post {
            flushInput()
            flushOutput()
        }

        override fun onClosed(error: String?) = post { endSession(error) }
    }

    private fun endSession(error: String?) {
        sessionId++
        idleJob?.cancel()
        idleJob = null
        capture?.stop()
        capture = null
        client?.close()
        client = null
        player?.stop()
        player = null
        flushInput()
        flushOutput()
        _ui.update {
            it.copy(
                voiceState = VoiceState.IDLE,
                level = 0f,
                partialText = "",
                micLabel = "",
                error = error ?: it.error,
            )
        }
    }

    /** Un micro resté ouvert capte la cuisine entière : on le coupe après quelques minutes de silence. */
    private fun resetIdleTimer() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(IDLE_TIMEOUT_MS)
            endSession("Micro coupé après 3 minutes sans voix. Appuyez sur le micro pour reprendre.")
        }
    }

    private fun flushInput() {
        val text = inputBuffer.toString().trim()
        inputBuffer.clear()
        if (text.isNotEmpty()) addLine(fromUser = true, text = text)
        _ui.update { it.copy(partialText = "") }
    }

    private fun flushOutput() {
        val text = outputBuffer.toString().trim()
        outputBuffer.clear()
        if (text.isNotEmpty()) addLine(fromUser = false, text = text)
    }

    // ---- Outils appelés par Gemini -----------------------------------------------------------

    private suspend fun runTool(call: ToolCall): JSONObject = try {
        when (call.name) {
            OrderTools.ADD -> toolAdd(call.args)
            OrderTools.REMOVE -> toolRemove(call.args)
            OrderTools.READ -> orderJson()
            OrderTools.VALIDATE -> toolValidate()
            OrderTools.CANCEL -> toolCancel()
            else -> JSONObject().put("error", "Outil inconnu : ${call.name}")
        }
    } catch (e: Exception) {
        JSONObject().put("error", "L'opération a échoué : ${e.message}")
    }

    private suspend fun menuParser(): CommandParser =
        CommandParser(repo.availablePizzas().map { MenuEntry(it.id, it.name) })

    private suspend fun toolAdd(args: JSONObject): JSONObject {
        val parser = menuParser()
        val results = JSONArray()
        val items = args.optJSONArray("items") ?: JSONArray()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val requested = item.optString("pizza")
            val quantity = item.optInt("quantity", 1).coerceAtLeast(1)
            results.put(
                when (val match = parser.resolve(requested)) {
                    is MenuMatch.Found ->
                        if (repo.addToDraft(match.entry.id, quantity)) {
                            JSONObject().put("pizza", match.entry.name).put("status", "added").put("quantity", quantity)
                        } else {
                            JSONObject().put("pizza", match.entry.name).put("status", "unavailable")
                        }
                    is MenuMatch.Ambiguous ->
                        JSONObject().put("requested", requested).put("status", "ambiguous")
                            .put("options", JSONArray(match.names))
                    MenuMatch.NotFound ->
                        JSONObject().put("requested", requested).put("status", "not_on_menu")
                }
            )
        }
        return orderJson().put("results", results)
    }

    private suspend fun toolRemove(args: JSONObject): JSONObject {
        val parser = menuParser()
        val results = JSONArray()
        val items = args.optJSONArray("items") ?: JSONArray()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val requested = item.optString("pizza")
            val quantity = if (item.has("quantity")) item.optInt("quantity").coerceAtLeast(1) else null
            results.put(
                when (val match = parser.resolve(requested)) {
                    is MenuMatch.Found -> {
                        val removed = repo.removeFromDraft(match.entry.id, quantity)
                        JSONObject().put("pizza", match.entry.name)
                            .put("status", if (removed > 0) "removed" else "not_in_order")
                            .put("quantity", removed)
                    }
                    is MenuMatch.Ambiguous ->
                        JSONObject().put("requested", requested).put("status", "ambiguous")
                            .put("options", JSONArray(match.names))
                    MenuMatch.NotFound ->
                        JSONObject().put("requested", requested).put("status", "not_on_menu")
                }
            )
        }
        return orderJson().put("results", results)
    }

    private suspend fun toolValidate(): JSONObject = when (val result = repo.confirmDraft()) {
        ConfirmResult.Empty -> JSONObject().put("status", "empty_order")
        is ConfirmResult.Confirmed -> JSONObject()
            .put("status", "validated")
            .put("ticket_number", result.shortNumber)
            .put("total", Format.price(result.totalCents))
        is ConfirmResult.Failed -> JSONObject().put("status", "failed").put("error", result.reason)
    }

    private suspend fun toolCancel(): JSONObject =
        JSONObject().put("status", if (repo.cancelDraft()) "cancelled" else "nothing_to_cancel")

    private fun orderJson(): JSONObject {
        val current = repo.currentDraft()
        val lines = JSONArray()
        current.lines.forEach {
            lines.put(
                JSONObject().put("pizza", it.name).put("quantity", it.quantity)
                    .put("unit_price", Format.price(it.unitPriceCents))
            )
        }
        return JSONObject().put("order", lines).put("total", Format.price(current.totalCents))
    }

    // ---- Moteur Android + saisie clavier : analyse locale ------------------------------------

    private fun startAndroidSpeech() {
        _ui.update { it.copy(engine = VoiceEngine.ANDROID, micLabel = "") }
        speech.start()
    }

    private fun handleUtterance(alternatives: List<String>) {
        viewModelScope.launch {
            addLine(fromUser = true, text = alternatives.first())
            val parser = menuParser()
            say(executeLocal(parser.parseBest(alternatives)))
        }
    }

    private suspend fun executeLocal(commands: List<VoiceCommand>): String {
        if (commands.isEmpty()) {
            return "Je n'ai pas compris. Essayez : « deux margherita et une reine »."
        }
        val replies = mutableListOf<String>()
        var orderChanged = false
        var finished = false

        for (command in commands) {
            when (command) {
                is VoiceCommand.Add -> {
                    if (repo.addToDraft(command.itemId, command.quantity)) {
                        orderChanged = true
                        replies += "J'ajoute ${command.quantity} ${command.itemName}."
                    } else {
                        replies += "${command.itemName} n'est plus disponible."
                    }
                }
                is VoiceCommand.Remove -> {
                    val removed = repo.removeFromDraft(command.itemId, command.quantity)
                    if (removed > 0) {
                        orderChanged = true
                        replies += "Je retire $removed ${command.itemName}."
                    } else {
                        replies += "${command.itemName} n'est pas dans la commande."
                    }
                }
                is VoiceCommand.Ambiguous ->
                    replies += "Précisez : ${command.options.joinToString(" ou ")} ?"
                is VoiceCommand.Unknown ->
                    replies += "Je n'ai pas compris « ${command.heard} »."
                VoiceCommand.ReadBack -> replies += readBack()
                VoiceCommand.Confirm -> {
                    finished = true
                    replies += when (val result = repo.confirmDraft()) {
                        ConfirmResult.Empty -> "La commande est vide, il n'y a rien à valider."
                        is ConfirmResult.Confirmed ->
                            "Ticket numéro ${result.shortNumber} validé. Total : ${Format.price(result.totalCents)}."
                        is ConfirmResult.Failed ->
                            "Impossible d'enregistrer le ticket. La commande est conservée, réessayez."
                    }
                }
                VoiceCommand.CancelOrder -> {
                    finished = true
                    replies += if (repo.cancelDraft()) "Commande annulée." else "Il n'y a rien à annuler."
                }
            }
        }

        if (orderChanged && !finished) {
            val current = repo.currentDraft()
            if (!current.isEmpty) replies += "Total : ${Format.price(current.totalCents)}."
        }
        return replies.joinToString(" ")
    }

    private fun readBack(): String {
        val current = repo.currentDraft()
        if (current.isEmpty) return "Votre commande est vide."
        val items = current.lines.joinToString(", ") { "${it.quantity} ${it.name}" }
        return "Vous avez : $items. Total : ${Format.price(current.totalCents)}."
    }

    private fun say(text: String) {
        addLine(fromUser = false, text = text)
        // Pendant une session Gemini, c'est le modèle qui parle : pas de voix de synthèse en double.
        if (_ui.value.speakReplies && client == null) tts.speak(text)
    }

    private fun addLine(fromUser: Boolean, text: String) {
        _ui.update { it.copy(transcript = (it.transcript + ChatLine(nextLineId++, fromUser, text)).takeLast(MAX_LINES)) }
    }

    override fun onCleared() {
        endSession(null)
        speech.destroy()
        tts.shutdown()
    }

    private companion object {
        const val IDLE_TIMEOUT_MS = 3 * 60 * 1000L
        const val MAX_LINES = 60
    }
}
