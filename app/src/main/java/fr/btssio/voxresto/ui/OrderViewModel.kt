package fr.btssio.voxresto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.btssio.voxresto.VoxRestoApp
import fr.btssio.voxresto.data.ConfirmResult
import fr.btssio.voxresto.data.InputSource
import fr.btssio.voxresto.data.TicketWithDetails
import fr.btssio.voxresto.voice.CommandParser
import fr.btssio.voxresto.voice.MenuEntry
import fr.btssio.voxresto.voice.SpeechManager
import fr.btssio.voxresto.voice.SpeechOutput
import fr.btssio.voxresto.voice.VoiceCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VoiceState { IDLE, LISTENING, PROCESSING }

data class ChatLine(val id: Long, val fromUser: Boolean, val text: String)

data class OrderUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val level: Float = 0f,
    val partialText: String = "",
    val transcript: List<ChatLine> = emptyList(),
    val error: String? = null,
    val speakReplies: Boolean = true,
    val speechAvailable: Boolean = true,
)

class OrderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as VoxRestoApp).repository
    private val tts = SpeechOutput(app)
    private var nextLineId = 1L

    private val _ui = MutableStateFlow(
        OrderUiState(
            transcript = listOf(
                ChatLine(0, false, "Bonjour ! Appuyez sur le micro et dites par exemple : « deux samoussas et un coca ».")
            )
        )
    )
    val uiState: StateFlow<OrderUiState> = _ui.asStateFlow()

    /** Commande en cours (ticket DRAFT), mise à jour en direct depuis la BDD. */
    val draft: StateFlow<TicketWithDetails?> =
        repo.draft.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val callbacks = object : SpeechManager.Callbacks {
        override fun onListening() = _ui.update {
            it.copy(voiceState = VoiceState.LISTENING, partialText = "", error = null)
        }

        override fun onLevel(level: Float) = _ui.update { it.copy(level = level) }

        override fun onPartial(text: String) = _ui.update { it.copy(partialText = text) }

        override fun onProcessing() = _ui.update { it.copy(voiceState = VoiceState.PROCESSING, level = 0f) }

        override fun onResults(alternatives: List<String>) {
            _ui.update { it.copy(voiceState = VoiceState.IDLE, partialText = "", level = 0f) }
            handleUtterance(alternatives, InputSource.VOICE)
        }

        override fun onError(message: String) = _ui.update {
            it.copy(voiceState = VoiceState.IDLE, partialText = "", level = 0f, error = message)
        }
    }

    private val speech = SpeechManager(app, callbacks)

    init {
        viewModelScope.launch { repo.ensureSeeded() }
        _ui.update { it.copy(speechAvailable = speech.isAvailable) }
    }

    // ---- Actions de l'écran ------------------------------------------------------------------

    fun startListening() {
        tts.stop()
        _ui.update { it.copy(error = null) }
        speech.start()
    }

    fun stopListening() {
        speech.stop()
    }

    fun onPermissionDenied() {
        _ui.update { it.copy(error = "Autorisation du micro refusée : vous pouvez écrire votre commande.") }
    }

    fun toggleSpeakReplies() {
        val enabled = !_ui.value.speakReplies
        if (!enabled) tts.stop()
        _ui.update { it.copy(speakReplies = enabled) }
    }

    fun submitText(text: String) {
        if (text.isBlank()) return
        handleUtterance(listOf(text), InputSource.TEXT)
    }

    fun increment(itemId: Long) {
        viewModelScope.launch { repo.addItem(itemId, 1, InputSource.TOUCH) }
    }

    fun decrement(itemId: Long) {
        viewModelScope.launch { repo.removeItem(itemId, 1, InputSource.TOUCH) }
    }

    fun confirm() {
        viewModelScope.launch { say(execute(listOf(VoiceCommand.Confirm), InputSource.TOUCH)) }
    }

    fun cancel() {
        viewModelScope.launch { say(execute(listOf(VoiceCommand.CancelOrder), InputSource.TOUCH)) }
    }

    // ---- Traitement d'une phrase -------------------------------------------------------------

    private fun handleUtterance(alternatives: List<String>, source: InputSource) {
        viewModelScope.launch {
            addLine(fromUser = true, text = alternatives.first())
            val menu = repo.availableMenu().map { MenuEntry(it.id, it.name, it.aliasList) }
            val commands = CommandParser(menu).parseBest(alternatives)
            say(execute(commands, source))
        }
    }

    private suspend fun execute(commands: List<VoiceCommand>, source: InputSource): String {
        if (commands.isEmpty()) {
            return "Je n'ai pas compris. Essayez : « deux samoussas et un coca »."
        }
        val replies = mutableListOf<String>()
        var orderChanged = false
        var finished = false

        for (command in commands) {
            when (command) {
                is VoiceCommand.Add -> {
                    if (repo.addItem(command.itemId, command.quantity, source)) {
                        orderChanged = true
                        replies += "J'ajoute ${command.quantity} ${command.itemName}."
                    } else {
                        replies += "${command.itemName} n'est plus disponible."
                    }
                }
                is VoiceCommand.Remove -> {
                    val removed = repo.removeItem(command.itemId, command.quantity, source)
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
                    replies += when (val result = repo.confirmDraft(source)) {
                        ConfirmResult.Empty -> "La commande est vide, il n'y a rien à valider."
                        is ConfirmResult.Confirmed ->
                            "Commande numéro ${result.number} validée. Total : ${Format.price(result.totalCents)}."
                    }
                }
                VoiceCommand.CancelOrder -> {
                    finished = true
                    replies += if (repo.cancelDraft(source)) "Commande annulée." else "Il n'y a rien à annuler."
                }
            }
        }

        if (orderChanged && !finished) {
            repo.currentDraft()?.let { replies += "Total : ${Format.price(it.totalCents)}." }
        }
        return replies.joinToString(" ")
    }

    private suspend fun readBack(): String {
        val current = repo.currentDraft()
        if (current == null || current.lines.isEmpty()) return "Votre commande est vide."
        val items = current.lines.sortedBy { it.id }.joinToString(", ") { "${it.quantity} ${it.name}" }
        return "Vous avez : $items. Total : ${Format.price(current.totalCents)}."
    }

    private fun say(text: String) {
        addLine(fromUser = false, text = text)
        if (_ui.value.speakReplies) tts.speak(text)
    }

    private fun addLine(fromUser: Boolean, text: String) {
        _ui.update { it.copy(transcript = it.transcript + ChatLine(nextLineId++, fromUser, text)) }
    }

    override fun onCleared() {
        speech.destroy()
        tts.shutdown()
    }
}
