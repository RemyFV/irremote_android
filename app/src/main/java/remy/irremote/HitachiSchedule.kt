package remy.irremote

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

// weekly schedule, sent like the remote's SEND key: state frame, clock, then one frame per day mon..sun
// (type 11, payload: day 1..7 then 2 bytes per slot, temp*2 or ff for off and hour<<3 | minute/10).
// always 9 frames, empty days included: the remote was only captured with slots on every day, so that
// part is a guess. the unit stores the last schedule it got, A/B are just two memories on the remote.
// on slots only carry a temperature: the mode comes from the state frame sent with them
object HitachiSchedule {
    const val MAX = 6
    data class Slot(val days: Set<Int> = (1..7).toSet(), val hour: Int = 7, val minute: Int = 30, val on: Boolean = true, val temp: Int = 21)

    fun load(ctx: Context): List<Slot> = JSONArray(HitachiAc.prefs(ctx).getString("slots", "[]")).let { a ->
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val d = o.getJSONArray("days")
            Slot((0 until d.length()).map { d.getInt(it) }.toSet(), o.getInt("h"), o.getInt("m"), o.getBoolean("on"), o.getInt("t"))
        }
    }

    // saves, transmits, and returns the state that went with it (schedule flag on, like the remote does)
    fun send(ctx: Context, state: HitachiAc.State, slots: List<Slot>): HitachiAc.State {
        val json = JSONArray(slots.map { JSONObject().put("days", JSONArray(it.days.sorted())).put("h", it.hour).put("m", it.minute).put("on", it.on).put("t", it.temp) })
        HitachiAc.prefs(ctx).edit().putString("slots", json.toString()).apply()
        val s = state.copy(schedule = true).fixed()
        HitachiAc.save(ctx, s)
        val days = (1..7).map { d ->
            intArrayOf(d) + slots.filter { d in it.days }.sortedWith(compareBy({ it.hour }, { it.minute }))
                .flatMap { listOf(if (it.on) it.temp * 2 else 0xFF, (it.hour shl 3) or (it.minute / 10)) }
        }
        HitachiAc.transmit(ctx, listOf(HitachiAc.stateFrame(s, 0x19), HitachiAc.clockFrame(0x29)) +
            days.mapIndexed { i, p -> HitachiAc.frame(0x11, ((i + 3) shl 4) or 9, p) })
        return s
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSection(ctx: Context, send: (List<HitachiSchedule.Slot>) -> Unit) {
    var slots by remember { mutableStateOf(HitachiSchedule.load(ctx)) }
    var editing by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("schedule (mode from the form above)", Modifier.weight(1f), fontSize = 13.sp)
            TextButton({ slots = slots + HitachiSchedule.Slot() }, enabled = slots.size < HitachiSchedule.MAX) { Text("+ slot") }
        }
        slots.forEachIndexed { i, sl ->
            val set = { n: HitachiSchedule.Slot -> slots = slots.toMutableList().also { it[i] = n } }
            MultiChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                "MTWTFSS".forEachIndexed { d, c ->
                    SegmentedButton(d + 1 in sl.days, { set(sl.copy(days = if (it) sl.days + (d + 1) else sl.days - (d + 1))) },
                        SegmentedButtonDefaults.itemShape(d, 7), icon = {}) { Text("$c") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ editing = i }) { Text("%02d:%02d".format(sl.hour, sl.minute), fontSize = 20.sp) }
                Choice(listOf(1 to "ON", 0 to "OFF"), if (sl.on) 1 else 0, Modifier.weight(1f)) { set(sl.copy(on = it == 1)) }
                if (sl.on) {
                    Small("-", sl.temp > 16) { set(sl.copy(temp = sl.temp - 1)) }
                    Text("${sl.temp}°")
                    Small("+", sl.temp < 32) { set(sl.copy(temp = sl.temp + 1)) }
                }
                Small("✕") { slots = slots.filterIndexed { j, _ -> j != i } }
            }
        }
        Button({ send(slots) }, Modifier.fillMaxWidth().height(56.dp), enabled = slots.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(buttonColor("blue")), contentColor = Color.White)) {
            Text("SEND SCHEDULE")
        }
    }

    editing?.let { i ->
        val t = rememberTimePickerState(slots[i].hour, slots[i].minute, is24Hour = true)
        AlertDialog({ editing = null }, confirmButton = {
            TextButton({
                // the remote only does 10 minute steps
                slots = slots.toMutableList().also { it[i] = it[i].copy(hour = t.hour, minute = t.minute / 10 * 10) }
                editing = null
            }) { Text("OK") }
        }, text = { TimePicker(t) })
    }
}

@Composable
private fun Small(label: String, enabled: Boolean = true, onClick: () -> Unit) =
    TextButton(onClick, Modifier.size(36.dp), enabled = enabled, contentPadding = PaddingValues(0.dp)) { Text(label, fontSize = 18.sp) }
