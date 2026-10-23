package ro.florin.bateriexperia

import android.content.Context
import java.io.File
import java.util.Locale

data class Sample(
    val ts: Long,
    val level: Int,
    val tempC: Float,
    val voltMv: Int,
    val currentUa: Long,
    val chargeUah: Long,
    val status: Int,
    val plugged: Int
) {
    val estCapMah: Float
        get() = if (level >= 10 && chargeUah > 0) chargeUah / 1000f / (level / 100f) else Float.NaN
}

/** Istoricul măsurătorilor, salvat într-un fișier CSV în spațiul privat al aplicației. */
class SampleStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "samples.csv")

    fun append(r: Reading) {
        synchronized(LOCK) {
            val line = String.format(
                Locale.US, "%d,%d,%.1f,%d,%d,%d,%d,%d\n",
                r.ts, r.level, r.tempC, r.voltMv, r.currentUa, r.chargeUah, r.status, r.plugged
            )
            file.appendText(line)
            if (file.length() > MAX_BYTES) prune()
        }
    }

    fun load(sinceMs: Long): List<Sample> {
        synchronized(LOCK) {
            if (!file.exists()) return emptyList()
            val out = ArrayList<Sample>()
            file.forEachLine { line ->
                parse(line)?.let { if (it.ts >= sinceMs) out.add(it) }
            }
            out.sortBy { it.ts }
            return out
        }
    }

    fun clear() {
        synchronized(LOCK) { file.delete() }
    }

    private fun prune() {
        val limit = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600_000L
        val kept = ArrayList<String>()
        file.forEachLine { line ->
            val s = parse(line)
            if (s != null && s.ts >= limit) kept.add(line)
        }
        file.writeText(kept.joinToString("\n", postfix = if (kept.isEmpty()) "" else "\n"))
    }

    private fun parse(line: String): Sample? {
        val p = line.split(",")
        if (p.size < 8) return null
        return try {
            Sample(
                ts = p[0].toLong(),
                level = p[1].toInt(),
                tempC = p[2].toFloat(),
                voltMv = p[3].toInt(),
                currentUa = p[4].toLong(),
                chargeUah = p[5].toLong(),
                status = p[6].toInt(),
                plugged = p[7].toInt()
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    companion object {
        private val LOCK = Any()
        private const val MAX_BYTES = 600_000L
        private const val KEEP_DAYS = 60
    }
}
