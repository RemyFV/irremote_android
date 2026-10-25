package remy.irremote

import android.content.Context
import android.hardware.ConsumerIrManager

// Hitachi RAR-5E1 / RAR-6NE1 remote: every press sends the whole state (28 bytes, 38 kHz, LSB first),
// so the app has to remember mode/temp/fan itself. frame layout and timings from HeatpumpIR's
// HitachiHeatpumpIR.cpp, cross-checked against IRremoteESP8266's HITACHI_AC (same bytes, bit-reversed).
object HitachiAc {
    private const val HDR_MARK = 3436
    private const val HDR_SPACE = 1640
    private const val BIT_MARK = 420
    private const val ONE_SPACE = 1250
    private const val ZERO_SPACE = 500
    private const val GAP = 50_000

    private val TEMPLATE = intArrayOf(
        0x01, 0x10, 0x30, 0x40, 0xBF, 0x01, 0xFE, 0x11, 0x12, 0x08, 0x00, 0x00, 0x00, 0x00,
        0x06, 0x06, 0x00, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0x01, 0x00, 0x00, 0x00)

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

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("hitachi_ac", Context.MODE_PRIVATE)

    fun load(ctx: Context): State = prefs(ctx).let { p ->
        val d = State()
        State(p.getBoolean("power", d.power), p.getInt("mode", d.mode), p.getInt("temp", d.temp),
            p.getInt("fan", d.fan), p.getBoolean("swingV", d.swingV), p.getBoolean("swingH", d.swingH),
            p.getBoolean("schedule", d.schedule))
    }

    // saves and transmits; returns what was actually sent (after fixed())
    fun send(ctx: Context, state: State): State {
        val s = state.fixed()
        prefs(ctx).edit().putBoolean("power", s.power).putInt("mode", s.mode).putInt("temp", s.temp)
            .putInt("fan", s.fan).putBoolean("swingV", s.swingV).putBoolean("swingH", s.swingH)
            .putBoolean("schedule", s.schedule).apply()
        transmit(ctx, frame(s))
        return s
    }

    fun transmit(ctx: Context, pattern: IntArray) {
        ctx.getSystemService(ConsumerIrManager::class.java)?.takeIf { it.hasIrEmitter() }?.transmit(38000, pattern)
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

    // or: extra bits per byte index, ORed in before the checksum (DebugReceiver / tools/bitsearch.py)
    fun frame(s: State, or: Map<Int, Int> = emptyMap()): IntArray {
        val temp = if (s.mode == MODE_FAN) 64 else s.temp
        val b = TEMPLATE.copyOf()
        if (temp == 16) b[9] = 0x09
        b[10] = s.mode
        b[11] = (temp shl 1) and 0xFF
        b[13] = s.fan
        if (s.swingV) b[14] = b[14] or 0x01
        if (s.swingH) b[15] = b[15] or 0x01
        b[17] = if (s.power) 0x80 else 0x00
        // weekly timer enabled: found by tools/bitsearch.py, the unit keeps the schedule stored in it but
        // disables it on any frame with this bit clear. a bit in bytes 22-26 triggers self clean, not pinned yet
        if (s.schedule) b[18] = b[18] or 0x01
        for ((i, v) in or) b[i] = b[i] or v
        b[27] = (1086 - b.take(27).sum()) and 0xFF
        val p = mutableListOf(HDR_MARK, HDR_SPACE)
        for (byte in b) for (i in 0 until 8) p += listOf(BIT_MARK, if ((byte shr i) and 1 == 1) ONE_SPACE else ZERO_SPACE)
        p += listOf(BIT_MARK, GAP)
        return p.toIntArray()
    }
}
