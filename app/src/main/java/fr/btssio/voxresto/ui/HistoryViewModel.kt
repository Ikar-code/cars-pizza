package fr.btssio.voxresto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.btssio.voxresto.VoxRestoApp
import fr.btssio.voxresto.data.InputSource
import fr.btssio.voxresto.data.TicketWithDetails
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as VoxRestoApp).repository

    val history: StateFlow<List<TicketWithDetails>> =
        repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markReady(ticketId: Long) {
        viewModelScope.launch { repo.markReady(ticketId, InputSource.TOUCH) }
    }

    fun cancel(ticketId: Long) {
        viewModelScope.launch { repo.cancelTicket(ticketId, InputSource.TOUCH) }
    }
}
