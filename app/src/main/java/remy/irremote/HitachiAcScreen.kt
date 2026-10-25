package remy.irremote

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// fields edit a local copy, SEND transmits it; POWER transmits right away with the fields as shown
@Composable
fun HitachiAcScreen(ctx: Context, onSend: () -> Unit) {
    var s by remember { mutableStateOf(HitachiAc.load(ctx)) }
    var sent by remember { mutableStateOf(s) }
    val send = { st: HitachiAc.State -> sent = HitachiAc.send(ctx, st); s = sent; onSend() }

    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("last sent: ${label(sent)}", fontSize = 13.sp)

        Choice(HitachiAc.MODES, s.mode) { s = s.copy(mode = it) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Round("-", 0xFF1565C0, s.temp > 16) { s = s.copy(temp = s.temp - 1) }
            Text("${s.temp}°C", Modifier.width(110.dp), fontSize = 28.sp, textAlign = TextAlign.Center)
            Round("+", 0xFFEF6C00, s.temp < 32) { s = s.copy(temp = s.temp + 1) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_fan), "fan speed", Modifier.padding(start = 4.dp))
            Choice(HitachiAc.FANS, s.fan) { s = s.copy(fan = it) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle("Swing V", s.swingV, Modifier.weight(1f)) { s = s.copy(swingV = !s.swingV) }
            Toggle("Swing H", s.swingH, Modifier.weight(1f)) { s = s.copy(swingH = !s.swingH) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ send(s.copy(power = !sent.power)) }, Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(buttonColor("red")), contentColor = Color.White)) {
                Text(if (sent.power) "POWER OFF" else "POWER ON")
            }
            Button({ send(s.copy(power = true)) }, Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(buttonColor("green")), contentColor = Color.White)) {
                Text("SEND")
            }
        }
    }
}

@Composable
private fun Round(label: String, color: Long, enabled: Boolean, onClick: () -> Unit) =
    Button(onClick, Modifier.size(56.dp), enabled = enabled, shape = CircleShape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(color), contentColor = Color.White)) {
        Text(label, fontSize = 24.sp)
    }

@Composable
private fun Toggle(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) =
    FilterChip(on, onClick, { Text(label) }, modifier,
        trailingIcon = if (on) ({
            // same check as the segmented rows, material-icons isn't on the classpath for a direct Icon()
            CompositionLocalProvider(LocalContentColor provides Color(0xFF43A047)) { SegmentedButtonDefaults.Icon(true) }
        }) else null)

@Composable
private fun Choice(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) =
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, name) ->
            SegmentedButton(v == selected, { onSelect(v) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(name) }
        }
    }

private fun label(s: HitachiAc.State): String {
    val mode = HitachiAc.MODES.first { it.first == s.mode }.second
    val fan = HitachiAc.FANS.first { it.first == s.fan }.second.lowercase()
    val swing = listOfNotNull("V".takeIf { s.swingV }, "H".takeIf { s.swingH }).joinToString("+").ifEmpty { "off" }
    return "$mode ${s.temp}°C  fan $fan  swing $swing  ${if (s.power) "ON" else "OFF"}"
}
