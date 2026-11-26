package fr.btssio.carspizza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.btssio.carspizza.ui.CarsPizzaTheme
import fr.btssio.carspizza.ui.CarsPizzaUi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarsPizzaTheme {
                CarsPizzaUi()
            }
        }
    }
}
