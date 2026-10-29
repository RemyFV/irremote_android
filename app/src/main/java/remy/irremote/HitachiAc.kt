package remy.irremote

import android.content.Context
import android.hardware.ConsumerIrManager
import java.util.Calendar

// Hitachi RAR-6NE1 remote, decoded from captures of the real thing (tools/decode.py on ir-ctl output).
// 38 kHz, 3400/1700 header, 430 mark, 500 / 1250 space, LSB first. every frame is
//   01 10 30 40 bf, type, ~type, index<<4 | frames in this key press, payload length, payload, checksum
// with the checksum chosen so all bytes sum to 0x3e mod 256. a key press sends the 28-byte state frame
// (type 01), and for power / mode keys the 16-byte clock frame (type 10: yy mm dd hh mm weekday, mon = 1).
// the unit keeps its own clock and the weekly schedule it was last sent, see HitachiSchedule.
// the app has to remember mode/temp/fan itself since every frame carries the whole state.
object HitachiAc {
    private const val HDR_MARK = 3436
    private const val HDR_SPACE = 1640
    private const val BIT_MARK = 420
    private const val ONE_SPACE = 1250
    private const val ZERO_SPACE = 500
    private const val GAP = 50_000

    private val PREFIX = intArrayOf(0x01, 0x10, 0x30, 0x40, 0xBF)
    // state payload: key code (08 = temp up, the unit ignores it), mode, temp*2 (64 in fan mode), 00, fan,
    // swing V bit 0, swing H bit 0, 00, power 80, sleep timer minutes, 00 00 00, weekly timer enabled 40,
    // 80 01, eco 02 / powerful 20, silent 08. the remote sends 0c in the eco byte, meaning unknown, 00 works
    private val STATE = intArrayOf(0x08, 0, 0, 0, 0, 0x06, 0x06, 0, 0, 0, 0, 0, 0, 0, 0x80, 0x01, 0, 0)

    const val MODE_AUTO = 0x02
    const val MODE_HEAT = 0x03
    const val MODE_COOL = 0x04
    const val MODE_DRY = 0x05
    const val MODE_FAN = 0x0C
    val MODES = listOf(MODE_AUTO to "AUTO", MODE_HEAT to "HEAT", MODE_COOL to "COOL", MODE_DRY to "DRY", MODE_FAN to "FAN")
    const val FAN_AUTO = 0x01  // then 0x02..0x05 for speeds 1-4
    val FANS = listOf(FAN_AUTO to "AUTO") + (1..4).map { FAN_AUTO + it to "$it" }

    data class State(
        val power: Boolean = false, val mode: Int = MODE_HEAT, val temp: Int = 23, val fan: Int = FAN_AUTO,
        val swingV: Boolean = false, val swingH: Boolean = false, val schedule: Boolean = true,
    ) {
        // the real remote won't let you pick these either: dry only has speeds 1-2, fan mode has no auto
        fun fixed() = copy(
            temp = temp.coerceIn(16, 32),
            fan = when {
                mode == MODE_DRY -> fan.coerceIn(FAN_AUTO + 1, FAN_AUTO + 2)
                mode == MODE_FAN && fan == FAN_AUTO -> FAN_AUTO + 1
                else -> fan
            },
        )
    }

    fun prefs(ctx: Context) = ctx.getSharedPreferences("hitachi_ac", Context.MODE_PRIVATE)

    fun load(ctx: Context): State = prefs(ctx).let { p ->
        val d = State()
        State(p.getBoolean("power", d.power), p.getInt("mode", d.mode), p.getInt("temp", d.temp),
            p.getInt("fan", d.fan), p.getBoolean("swingV", d.swingV), p.getBoolean("swingH", d.swingH),
            p.getBoolean("schedule", d.schedule))
    }

    fun save(ctx: Context, s: State) = prefs(ctx).edit().putBoolean("power", s.power).putInt("mode", s.mode)
        .putInt("temp", s.temp).putInt("fan", s.fan).putBoolean("swingV", s.swingV).putBoolean("swingH", s.swingH)
        .putBoolean("schedule", s.schedule).apply()

    // saves and transmits state + clock like the remote's power key; returns what was actually sent (after fixed())
    fun send(ctx: Context, state: State): State {
        val s = state.fixed()
        save(ctx, s)
        transmit(ctx, listOf(stateFrame(s, 0x12), clockFrame(0x22)))
        return s
    }

    // one-shot actions for widget buttons (remotes.json "hitachi_ac" entry); anything but power also turns the unit on
    fun press(ctx: Context, action: String) {
        val s = load(ctx)
        send(ctx, when (action) {
            "power" -> s.copy(power = !s.power)
            "on" -> s.copy(power = true)
            "off" -> s.copy(power = false)
            "temp+" -> s.copy(temp = s.temp + 1, power = true)
            "temp-" -> s.copy(temp = s.temp - 1, power = true)
            "fan" -> s.copy(fan = if (s.fan >= FAN_AUTO + 4) FAN_AUTO else s.fan + 1, power = true)
            "swingV" -> s.copy(swingV = !s.swingV, power = true)
            "swingH" -> s.copy(swingH = !s.swingH, power = true)
            "schedule" -> s.copy(schedule = !s.schedule, power = true)
            else -> MODES.firstOrNull { "mode:${it.second.lowercase()}" == action }?.let { s.copy(mode = it.first, power = true) }
                ?: error("unknown hitachi_ac action $action")
        })
    }

    // or: extra bits per frame byte index, ORed in before the checksum (DebugReceiver / tools/bitsearch.py)
    fun frame(type: Int, seq: Int, payload: IntArray, or: Map<Int, Int> = emptyMap()): IntArray {
        val b = PREFIX + intArrayOf(type, type.inv() and 0xFF, seq, payload.size) + payload + 0
        for ((i, v) in or) b[i] = b[i] or v
        b[b.lastIndex] = (0x3E - b.sum()) and 0xFF
        return b
    }

    fun stateFrame(s: State, seq: Int, or: Map<Int, Int> = emptyMap()): IntArray {
        val p = STATE.copyOf()
        p[1] = s.mode
        p[2] = ((if (s.mode == MODE_FAN) 64 else s.temp) shl 1) and 0xFF
        p[4] = s.fan
        if (s.swingV) p[5] = p[5] or 0x01
        if (s.swingH) p[6] = p[6] or 0x01
        if (s.power) p[8] = 0x80
        if (s.schedule) p[13] = p[13] or 0x40
        return frame(0x01, seq, p, or)
    }

    fun clockFrame(seq: Int): IntArray {
        val c = Calendar.getInstance()
        return frame(0x10, seq, intArrayOf(c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1))
    }

    // transmit() blocks until the pattern is out, so frames go one after the other with GAP between them
    fun transmit(ctx: Context, frames: List<IntArray>) {
        val ir = ctx.getSystemService(ConsumerIrManager::class.java)?.takeIf { it.hasIrEmitter() } ?: return
        for (f in frames) ir.transmit(38000, pulses(f))
    }

    private fun pulses(b: IntArray): IntArray {
        val p = mutableListOf(HDR_MARK, HDR_SPACE)
        for (byte in b) for (i in 0 until 8) p += listOf(BIT_MARK, if ((byte shr i) and 1 == 1) ONE_SPACE else ZERO_SPACE)
        p += listOf(BIT_MARK, GAP)
        return p.toIntArray()
    }
}
