package fr.btssio.voxresto

import android.app.Application
import fr.btssio.voxresto.data.AppDatabase
import fr.btssio.voxresto.data.OrderRepository

class VoxRestoApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: OrderRepository by lazy { OrderRepository(database) }
}
