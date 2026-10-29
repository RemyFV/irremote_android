package remy.irremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// adb shell am broadcast -n remy.irremote/.DebugReceiver --es or "18:ff,19:01"
// ORs the given bytes into the last sent hitachi frame and transmits it. only for hunting undocumented
// bits with tools/bitsearch.py, nothing in the app uses it.
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val or = intent.getStringExtra("or").orEmpty().split(",").filter { it.isNotBlank() }
            .associate { val (i, v) = it.split(":"); i.toInt() to v.toInt(16) }
        HitachiAc.transmit(ctx, listOf(HitachiAc.stateFrame(HitachiAc.load(ctx), 0x11, or)))
    }
}
