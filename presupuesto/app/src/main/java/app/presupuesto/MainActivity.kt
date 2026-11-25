package app.presupuesto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.presupuesto.ui.PantallaPrincipal
import app.presupuesto.ui.PresupuestoTema

class MainActivity : ComponentActivity() {

    private val vm: PresupuestoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PresupuestoTema {
                PantallaPrincipal(vm)
            }
        }
    }
}
