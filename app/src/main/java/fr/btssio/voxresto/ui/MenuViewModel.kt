package fr.btssio.voxresto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.btssio.voxresto.VoxRestoApp
import fr.btssio.voxresto.data.MenuItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MenuViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as VoxRestoApp).repository

    val menu: StateFlow<List<MenuItem>> =
        repo.menu.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAvailable(itemId: Long, available: Boolean) {
        viewModelScope.launch { repo.setAvailable(itemId, available) }
    }

    fun addItem(name: String, category: String, priceCents: Int, aliases: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.addMenuItem(name, category, priceCents, aliases)) }
    }
}
