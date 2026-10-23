package ro.florin.bateriexperia

import kotlin.math.floor
import kotlin.math.roundToInt

/** Transformă duratele în text românesc, scurt și ușor de citit. */
object Format {

    /** Durată dată în ore (ex. 5.5 -> "5 h 30 min"). */
    fun duration(hours: Double): String {
        if (hours.isNaN() || hours < 0) return "—"
        val totalMin = (hours * 60).roundToInt()
        if (totalMin < 1) return "sub 1 min"
        val days = totalMin / 1440
        val h = (totalMin % 1440) / 60
        val m = totalMin % 60
        return when {
            days >= 7 -> "peste 7 zile"
            days > 0 -> "$days z $h h"
            h > 0 -> "$h h $m min"
            else -> "$m min"
        }
    }

    /** Durată dată în luni (ex. 14.2 -> "1 an și 2 luni"). */
    fun months(months: Double): String {
        if (months.isNaN() || months < 0) return "—"
        if (months >= 60) return "peste 5 ani"
        val total = months.roundToInt()
        if (total < 1) return "sub o lună"
        val years = total / 12
        val rest = total % 12
        val y = when (years) {
            0 -> ""
            1 -> "1 an"
            else -> "$years ani"
        }
        val mo = when (rest) {
            0 -> ""
            1 -> "1 lună"
            else -> "$rest luni"
        }
        return when {
            y.isEmpty() -> mo
            mo.isEmpty() -> y
            else -> "$y și $mo"
        }
    }

    fun floorToInt(v: Double): Int = floor(v).toInt()
}
