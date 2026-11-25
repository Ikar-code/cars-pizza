package fr.btssio.voxresto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.btssio.voxresto.ui.VoxRestoTheme
import fr.btssio.voxresto.ui.VoxRestoUi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxRestoTheme {
                VoxRestoUi()
            }
        }
    }
}
