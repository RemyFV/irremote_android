package remy.irremote

import android.content.Context
import android.hardware.ConsumerIrManager
import org.json.JSONArray

// type: null for a real button, "blank" for an empty cell, "separator" for a full-width line
data class IrButton(val label: String, val code: String, val color: String?, val type: String? = null) {
    val isButton get() = type == null
}
data class Remote(val name: String, val protocol: String, val columns: Int, val buttons: List<IrButton>)

object Ir {
    @Volatile private var cache: List<Remote>? = null
    private var rc5Toggle = false

    fun remotes(ctx: Context): List<Remote> = cache ?: run {
        val arr = JSONArray(ctx.assets.open("remotes.json").bufferedReader().readText())
        List(arr.length()) { i ->
            val r = arr.getJSONObject(i)
            val bs = r.getJSONArray("buttons")
            Remote(r.getString("name"), r.getString("protocol"), r.getInt("columns"), List(bs.length()) { j ->
                val b = bs.getJSONObject(j)
                IrButton(b.optString("label"), b.optString("code"), b.optString("color").ifEmpty { null }, b.optString("type").ifEmpty { null })
            })
        }.also { cache = it }
    }

    fun send(ctx: Context, remote: Remote, button: IrButton) {
        if (!button.isButton) return
        if (remote.protocol == "hitachi_ac") return HitachiAc.press(ctx, button.code)
        val ir = ctx.getSystemService(ConsumerIrManager::class.java)?.takeIf { it.hasIrEmitter() } ?: return
        val (freq, pattern) = encode(remote.protocol, button.code)
        ir.transmit(freq, pattern)
    }

    fun encode(protocol: String, code: String): Pair<Int, IntArray> = when (protocol) {
        "pronto" -> pronto(code)
        "necx2" -> 38400 to necx2(code).let { it + it }  // sent twice, like IrCode Finder
        "rc5" -> 36000 to rc5(code, rc5Toggle).also { rc5Toggle = !rc5Toggle }
        else -> error("unknown protocol $protocol")
    }

    // pronto hex: 0000 <freq> <once pairs> <repeat pairs> <timings in carrier periods>; only the once sequence is sent
    private fun pronto(code: String): Pair<Int, IntArray> {
        val w = code.trim().split(Regex("\\s+")).map { it.toInt(16) }
        val freq = (1_000_000 / (w[1] * 0.241246)).toInt()
        val period = 1_000_000.0 / freq
        val once = w[2].takeIf { it > 0 } ?: w[3]
        return freq to IntArray(once * 2) { (w[4 + it] * period).toInt() }
    }

    // samsung32: 4500/4500 header, 32 bits MSB-first as written, frame padded to 108 ms
    private fun necx2(code: String): IntArray {
        val v = code.toLong(16)
        val p = mutableListOf(4500, 4500)
        for (i in 31 downTo 0) p += listOf(562, if ((v shr i) and 1L == 1L) 1687 else 562)
        p += 562
        p += 107_904 - p.sum()
        return p.toIntArray()
    }

    // 12-bit code = field bit (bit 11) + 5 address + 6 command; frame = 1, field, toggle, low 11 bits; Manchester, 889 us
    fun rc5(code: String, toggle: Boolean): IntArray {
        val v = code.toInt(16)
        val bits = listOf(1, (v shr 11) and 1, if (toggle) 1 else 0) + (10 downTo 0).map { (v shr it) and 1 }
        val p = mutableListOf(889)  // leading half-bit space of the first '1' is implicit
        for (i in 1 until bits.size) {
            if (bits[i] != bits[i - 1]) { p[p.size - 1] *= 2; p += 889 } else p += listOf(889, 889)
        }
        if (p.size % 2 == 0) p[p.size - 1] += 114_000 - p.sum() else p += 114_000 - p.sum()
        return p.toIntArray()
    }
}
