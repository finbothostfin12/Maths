package app.mathlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.mathlab.data.AppState
import app.mathlab.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = remember { AppState(applicationContext) }
            AppTheme(app) { MathLabApp(app) }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("Calc", Icons.Default.Calculate),
    TabItem("Graph", Icons.Default.Timeline),
    TabItem("Scan", Icons.Default.CameraAlt),
    TabItem("History", Icons.Default.History),
    TabItem("Settings", Icons.Default.Settings)
)

@Composable
fun MathLabApp(app: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val calc = remember { CalcState() }
    val graph = remember { GraphState() }
    val scan = remember { ScanState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> CalculatorScreen(app, calc)
                1 -> GraphScreen(app, graph)
                2 -> ScanScreen(app, scan)
                3 -> HistoryScreen(app) { expr -> calc.setText(expr); tab = 0 }
                else -> SettingsScreen(app)
            }
        }
    }
}
