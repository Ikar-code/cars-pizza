package fr.btssio.voxresto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.btssio.voxresto.VoxRestoApp
import fr.btssio.voxresto.data.TicketWithDetails
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** F6, F7, F10 : liste des commandes, changement d'état et historique. */
class OrdersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as VoxRestoApp).repository

    val tickets: StateFlow<List<TicketWithDetails>> =
        repo.tickets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** En attente → en préparation → terminée. */
    fun advance(ticketId: Long) {
        viewModelScope.launch { repo.advance(ticketId) }
    }

    fun cancel(ticketId: Long) {
        viewModelScope.launch { repo.cancelTicket(ticketId) }
    }
}
