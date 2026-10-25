package remy.irremote

import android.hardware.ConsumerIrManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val remotes = Ir.remotes(this)
        val prefs = getSharedPreferences("main", MODE_PRIVATE)
        val hasIr = getSystemService(ConsumerIrManager::class.java)?.hasIrEmitter() == true
        setContent {
            Screen {
                var tab by remember { mutableIntStateOf(prefs.getInt("tab", 0).coerceIn(remotes.indices)) }
                val haptic = LocalHapticFeedback.current
                if (!hasIr) Text("No IR emitter on this device", Modifier.padding(8.dp))
                var acStatus by remember { mutableStateOf(HitachiAc.status(this@MainActivity)) }
                RemoteTabs(remotes, tab) { tab = it; prefs.edit().putInt("tab", it).apply() }
                if (remotes[tab].protocol == "hitachi_ac") Text(acStatus, Modifier.padding(8.dp))
                RemoteGrid(remotes[tab]) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Ir.send(this@MainActivity, remotes[tab], remotes[tab].buttons[it])
                    acStatus = HitachiAc.status(this@MainActivity)
                }
            }
        }
    }
}
