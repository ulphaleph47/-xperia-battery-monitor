package ro.florin.bateriexperia

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlin.math.abs

/** Valoare folosită când sistemul nu raportează curentul. */
const val NA_LONG = Long.MIN_VALUE

data class Reading(
    val ts: Long,
    val level: Int,
    val tempC: Float,
    val voltMv: Int,
    val status: Int,
    val plugged: Int,
    val health: Int,
    val tech: String,
    val cycles: Int,
    val chargeUah: Long,
    val currentUa: Long,
    val soh: Int,
    val policy: Int,
    val mfgSec: Long,
    val firstUseSec: Long
) {
    /** Capacitatea totală estimată, în mAh (NaN dacă nu se poate calcula). */
    val estCapMah: Float
        get() = if (level >= 10 && chargeUah > 0) chargeUah / 1000f / (level / 100f) else Float.NaN

    /** Puterea momentană în wați (NaN dacă nu se cunoaște curentul). */
    val powerW: Double
        get() = if (currentUa == NA_LONG) Double.NaN else abs(currentUa) / 1e6 * (voltMv / 1000.0)
}

object BatteryReader {

    fun read(context: Context): Reading {
        val ctx = context.applicationContext
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        // Difuzare "sticky": fără receiver, returnează ultima stare cunoscută.
        val intent: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        var scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (scale <= 0) scale = 100
        val level = if (rawLevel < 0) bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) else rawLevel * 100 / scale

        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val volt = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

        var cycles = -1
        if (Build.VERSION.SDK_INT >= 34) {
            cycles = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        }

        val chargeUah = longProp(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        var soh = -1
        var policy = -1
        var mfg = -1L
        var firstUse = -1L
        if (Build.VERSION.SDK_INT >= 34) {
            soh = intProp(bm, BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH)
            policy = intProp(bm, BatteryManager.BATTERY_PROPERTY_CHARGING_POLICY)
            mfg = longProp(bm, BatteryManager.BATTERY_PROPERTY_MANUFACTURING_DATE)
            firstUse = longProp(bm, BatteryManager.BATTERY_PROPERTY_FIRST_USE_DATE)
        }

        return Reading(
            ts = System.currentTimeMillis(),
            level = level,
            tempC = tempC,
            voltMv = volt,
            status = status,
            plugged = plugged,
            health = health,
            tech = tech,
            cycles = if (cycles > 0) cycles else -1,
            chargeUah = chargeUah,
            currentUa = if (currentNow == Long.MIN_VALUE) NA_LONG else currentNow,
            soh = if (soh in 1..100) soh else -1,
            policy = policy,
            mfgSec = mfg,
            firstUseSec = firstUse
        )
    }

    private fun longProp(bm: BatteryManager, id: Int): Long {
        val v = bm.getLongProperty(id)
        return if (v == Long.MIN_VALUE || v <= 0L) -1L else v
    }

    private fun intProp(bm: BatteryManager, id: Int): Int {
        val v = bm.getIntProperty(id)
        return if (v == Int.MIN_VALUE) -1 else v
    }
}
