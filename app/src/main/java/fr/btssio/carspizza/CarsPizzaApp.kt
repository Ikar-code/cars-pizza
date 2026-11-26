package fr.btssio.carspizza

import android.app.Application
import fr.btssio.carspizza.data.OrderRepository
import fr.btssio.carspizza.data.PizzeriaDatabase

class CarsPizzaApp : Application() {
    val database: PizzeriaDatabase by lazy { PizzeriaDatabase.create(this) }
    val repository: OrderRepository by lazy { OrderRepository(database) }
    val settings: AppSettings by lazy { AppSettings(this) }
}
