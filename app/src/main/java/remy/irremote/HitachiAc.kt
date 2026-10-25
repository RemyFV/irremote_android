package remy.irremote

import android.content.Context

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
    const val FAN_AUTO = 0x01  // then 0x02..0x05 for speeds 1-4

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("hitachi_ac", Context.MODE_PRIVATE)

    fun status(ctx: Context): String = prefs(ctx).let { p ->
        val mode = when (p.getInt("mode", MODE_HEAT)) {
            MODE_AUTO -> "AUTO"; MODE_HEAT -> "HEAT"; MODE_COOL -> "COOL"; MODE_DRY -> "DRY"; else -> "FAN"
        }
        val fan = p.getInt("fan", FAN_AUTO).let { if (it == FAN_AUTO) "auto" else "${it - 1}" }
        "$mode ${p.getInt("temp", 23)}°C  fan $fan  ${if (p.getBoolean("power", false)) "ON" else "OFF"}"
    }

    // applies a button action to the saved state and returns the frame to send
    fun press(ctx: Context, action: String): Pair<Int, IntArray> {
        val p = prefs(ctx)
        var power = p.getBoolean("power", false)
        var temp = p.getInt("temp", 23)
        when (action) {
            "power" -> power = !power
            "temp+" -> { temp = (temp + 1).coerceAtMost(32); power = true }
            "temp-" -> { temp = (temp - 1).coerceAtLeast(16); power = true }
            else -> error("unknown hitachi_ac action $action")
        }
        p.edit().putBoolean("power", power).putInt("temp", temp).apply()
        return 38000 to frame(power, p.getInt("mode", MODE_HEAT), temp, p.getInt("fan", FAN_AUTO))
    }

    fun frame(power: Boolean, mode: Int, temp: Int, fan: Int): IntArray {
        val b = TEMPLATE.copyOf()
        if (temp == 16) b[9] = 0x09
        b[10] = mode
        b[11] = (temp shl 1) and 0xFF
        b[13] = fan
        b[17] = if (power) 0x80 else 0x00
        b[27] = (1086 - b.take(27).sum()) and 0xFF
        val p = mutableListOf(HDR_MARK, HDR_SPACE)
        for (byte in b) for (i in 0 until 8) p += listOf(BIT_MARK, if ((byte shr i) and 1 == 1) ONE_SPACE else ZERO_SPACE)
        p += listOf(BIT_MARK, GAP)
        return p.toIntArray()
    }
}
