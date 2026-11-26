package fr.btssio.voxresto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.btssio.voxresto.VoxRestoApp
import fr.btssio.voxresto.data.Pizza
import fr.btssio.voxresto.data.PizzaInput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** F9 : ajout, modification et suppression des pizzas et de leurs prix. */
class MenuViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as VoxRestoApp).repository

    val menu: StateFlow<List<Pizza>> =
        repo.pizzas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAvailable(pizzaId: Long, available: Boolean) {
        viewModelScope.launch { repo.setAvailable(pizzaId, available) }
    }

    /** [onResult] reçoit false si le nom est déjà pris. */
    fun save(pizzaId: Long?, input: PizzaInput, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(if (pizzaId == null) repo.createPizza(input) else repo.updatePizza(pizzaId, input))
        }
    }

    fun delete(pizzaId: Long) {
        viewModelScope.launch { repo.deletePizza(pizzaId) }
    }
}
