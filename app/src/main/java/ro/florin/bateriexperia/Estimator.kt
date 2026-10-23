package ro.florin.bateriexperia

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Toate calculele de estimare, fără dependențe de Android, ca să poată fi testate automat.
 * Rezultatele sunt orientative: depind de cât de bine raportează telefonul datele bateriei.
 */
object Estimator {

    private const val MAX_GAP_MS = 45 * 60_000L
    private const val HOUR_MS = 3_600_000.0

    /** De la acest nivel, încărcarea rapidă încetinește (în testele publicate, ultimele 20% durează ~2,5x mai mult). */
    private const val TAPER_START = 80.0
    private const val TAPER_FACTOR = 2.5

    /** Pragul sub care o baterie e considerată uzată (Sony indică peste 80% după 3 ani). */
    const val WEAR_LIMIT_PCT = 80.0

    /**
     * Nivelul exact al unei măsurători, în procente. Se folosește contorul de încărcare doar dacă e în
     * concordanță cu nivelul afișat de sistem; altfel (valori nerealiste) se folosește nivelul.
     */
    private fun pct(s: Sample, capMah: Double): Double {
        if (s.chargeUah > 0 && capMah > 0) {
            val fromCounter = s.chargeUah / 1000.0 / capMah * 100.0
            if (abs(fromCounter - s.level) <= 8.0) return fromCounter
        }
        return s.level.toDouble()
    }

    /**
     * Ritmul de variație al nivelului, în puncte procentuale pe oră, calculat din ultimul interval
     * continuu în care telefonul s-a descărcat (sau s-a încărcat). Valoarea e mereu pozitivă.
     * Returnează null dacă nu sunt suficiente date (interval sub 20 de minute sau variație prea mică).
     */
    fun segmentRatePerHour(
        samples: List<Sample>,
        now: Long,
        charging: Boolean,
        currentPct: Double,
        capMah: Double
    ): Double? {
        val window = if (charging) 3 * 3_600_000L else 12 * 3_600_000L
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        xs.add(now.toDouble())
        ys.add(currentPct)
        var prevTs = now
        for (i in samples.indices.reversed()) {
            val s = samples[i]
            if (s.ts > now) continue
            if (now - s.ts > window) break
            if (prevTs - s.ts > MAX_GAP_MS) break
            val match = if (charging) s.status == 2 else (s.plugged == 0 && s.status != 2)
            if (!match) break
            xs.add(s.ts.toDouble())
            ys.add(pct(s, capMah))
            prevTs = s.ts
        }
        if (xs.size < 2) return null
        val spanHours = (xs.first() - xs.last()) / HOUR_MS
        if (spanHours < 1.0 / 3.0) return null

        // regresie liniară: nivel în funcție de timp (în ore)
        val t0 = xs.last()
        val n = xs.size
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val x = (xs[i] - t0) / HOUR_MS
            sx += x
            sy += ys[i]
            sxx += x * x
            sxy += x * ys[i]
        }
        val denom = n * sxx - sx * sx
        if (denom <= 0.0) return null
        val slope = (n * sxy - sx * sy) / denom
        val rate = if (charging) slope else -slope
        val minRate = if (charging) 1.0 else 0.05
        return if (rate > minRate) rate else null
    }

    /** Consumul mediu, în puncte procentuale pe oră, doar din intervalele în care telefonul nu era conectat. */
    fun avgDischargeRate(samples: List<Sample>): Double? {
        var drop = 0.0
        var hours = 0.0
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            val gap = b.ts - a.ts
            if (gap <= 0 || gap > MAX_GAP_MS) continue
            if (a.status == 2 || b.status == 2 || a.plugged != 0 || b.plugged != 0) continue
            val d = a.level - b.level
            if (d < 0) continue
            drop += d
            hours += gap / HOUR_MS
        }
        return if (hours >= 0.5 && drop > 0) drop / hours else null
    }

    fun dischargeHoursLeft(levelPct: Double, ratePerHour: Double?): Double? =
        if (ratePerHour != null && ratePerHour > 0.05) levelPct / ratePerHour else null

    /** Autonomia din consumul de acum: capacitatea rămasă (mAh) împărțită la curentul mediu (mA). */
    fun hoursFromCurrent(remainingMah: Double, avgCurrentMa: Double): Double? =
        if (avgCurrentMa >= 20.0 && remainingMah > 0) remainingMah / avgCurrentMa else null

    /** Timp până la 100%, ținând cont că după 80% încărcarea încetinește. */
    fun chargeHoursLeft(levelPct: Double, ratePerHour: Double?): Double? {
        val r = ratePerHour ?: return null
        if (r < 1.0 || levelPct >= 100.0) return null
        return if (levelPct >= TAPER_START) {
            (100.0 - levelPct) / r
        } else {
            (TAPER_START - levelPct) / r + (100.0 - TAPER_START) / (r / TAPER_FACTOR)
        }
    }

    fun chargeHoursFromCurrent(levelPct: Double, capMah: Double, avgCurrentMa: Double): Double? {
        if (avgCurrentMa < 100.0 || capMah <= 0.0 || levelPct >= 100.0) return null
        return chargeHoursLeft(levelPct, avgCurrentMa / capMah * 100.0)
    }

    fun healthPct(capMah: Double, nominalMah: Double): Double = capMah / nominalMah * 100.0

    /**
     * Luni rămase până la pragul de 80%, presupunând o uzură constantă de la prima utilizare.
     * Null dacă nu se poate estima (prea puțin timp de utilizare sau nicio uzură măsurabilă).
     */
    fun monthsLeftToLimit(healthPct: Double, monthsInUse: Double?): Double? {
        if (monthsInUse == null || monthsInUse < 1.0) return null
        if (healthPct >= 100.0) return null
        if (healthPct <= WEAR_LIMIT_PCT) return 0.0
        val wearPerMonth = (100.0 - healthPct) / monthsInUse
        if (wearPerMonth < 0.02) return null
        return min((healthPct - WEAR_LIMIT_PCT) / wearPerMonth, 120.0)
    }

    /**
     * Luni rămase pe baza tendinței capacității măsurate în istoric (necesită cel puțin 25 de zile de date).
     */
    fun trendMonthsLeft(samples: List<Sample>, nominalMah: Double): Double? {
        val pts = samples.filter {
            val cap = it.estCapMah.toDouble()
            it.level in 20..95 && !cap.isNaN() && cap >= nominalMah * 0.5 && cap <= nominalMah * 1.5
        }
        if (pts.size < 50) return null
        val spanDays = (pts.last().ts - pts.first().ts) / 86_400_000.0
        if (spanDays < 25.0) return null
        val t0 = pts.first().ts
        val n = pts.size
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (p in pts) {
            val x = (p.ts - t0) / 86_400_000.0
            val y = p.estCapMah.toDouble()
            sx += x
            sy += y
            sxx += x * x
            sxy += x * y
        }
        val denom = n * sxx - sx * sx
        if (denom <= 0.0) return null
        val slope = (n * sxy - sx * sy) / denom // mAh pe zi
        if (slope >= -0.02) return null
        val intercept = (sy - slope * sx) / n
        val xNow = (pts.last().ts - t0) / 86_400_000.0
        val nowCap = intercept + slope * xNow
        val limitCap = nominalMah * WEAR_LIMIT_PCT / 100.0
        if (nowCap <= limitCap) return 0.0
        return min((nowCap - limitCap) / (-slope) / 30.44, 120.0)
    }

    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return Float.NaN
        val s = values.sorted()
        return s[s.size / 2]
    }

    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
}
