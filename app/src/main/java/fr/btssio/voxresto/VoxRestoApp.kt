package fr.btssio.voxresto

import android.app.Application
import fr.btssio.voxresto.data.OrderRepository
import fr.btssio.voxresto.data.PizzeriaDatabase

class VoxRestoApp : Application() {
    val database: PizzeriaDatabase by lazy { PizzeriaDatabase(this) }
    val repository: OrderRepository by lazy { OrderRepository(database) }
    val settings: AppSettings by lazy { AppSettings(this) }
}
