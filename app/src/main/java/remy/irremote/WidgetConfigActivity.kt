package remy.irremote

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val remotes = Ir.remotes(this)
        setContent {
            Screen {
                var tab by remember { mutableIntStateOf(0) }
                val picked = remember { mutableStateListOf<Int>() }
                Text("Pick up to 4 buttons", Modifier.padding(8.dp))
                RemoteTabs(remotes, tab) { tab = it; picked.clear() }
                Box(Modifier.weight(1f)) {
                    RemoteGrid(remotes[tab], highlighted = { it in picked }) {
                        if (it in picked) picked.remove(it) else if (picked.size < 4) picked.add(it)
                    }
                }
                Button(
                    onClick = {
                        RemoteWidget.save(this@WidgetConfigActivity, id, tab, picked.toList())
                        RemoteWidget.update(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), id)
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                ) { Text("Save") }
            }
        }
    }
}
