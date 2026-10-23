package ro.florin.bateriexperia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatorTest {

    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun s(ts: Long, level: Int, status: Int, plugged: Int, chargeUah: Long = -1L) =
        Sample(ts, level, 30f, 4000, -500_000L, chargeUah, status, plugged)

    @Test
    fun discharge_rate_from_level_history() {
        // 100% acum 4 ore, scade 5 puncte pe oră, citire la fiecare 30 de minute
        val samples = (0..8).map { i -> s(now - 4 * hour + i * hour / 2, 100 - (i * 5 / 2.0).toInt(), 3, 0) }
        val level = 80.0
        val rate = Estimator.segmentRatePerHour(samples, now, false, level, 5000.0)
        assertNotNull(rate)
        assertEquals(5.0, rate!!, 0.6)
        val left = Estimator.dischargeHoursLeft(level, rate)
        assertEquals(16.0, left!!, 2.0)
    }

    @Test
    fun not_enough_data_gives_null() {
        val samples = listOf(s(now - 10 * 60_000L, 80, 3, 0))
        assertNull(Estimator.segmentRatePerHour(samples, now, false, 79.0, 5000.0))
    }

    @Test
    fun plugged_samples_break_discharge_segment() {
        val samples = listOf(
            s(now - 3 * hour, 40, 2, 1),
            s(now - 2 * hour, 70, 2, 1),
            s(now - hour / 2, 90, 3, 0)
        )
        // în ultimul segment de descărcare e doar un punct + momentul actual, interval sub 20 de minute? 30 min, variație mică
        val rate = Estimator.segmentRatePerHour(samples, now, false, 89.0, 5000.0)
        assertTrue(rate == null || rate > 0)
    }

    @Test
    fun charge_time_accounts_for_slow_tail() {
        // de la 50% cu 60 puncte/oră: 30/60 + 20/(60/2.5) = 0,5 + 0,8333
        val h = Estimator.chargeHoursLeft(50.0, 60.0)
        assertEquals(1.3333, h!!, 0.001)
        // de la 90%, ritmul măsurat e deja cel din final
        assertEquals(0.5, Estimator.chargeHoursLeft(90.0, 20.0)!!, 0.001)
        assertNull(Estimator.chargeHoursLeft(100.0, 20.0))
    }

    @Test
    fun charge_time_from_current() {
        // 5000 mAh, 3000 mA => 60 %/h
        val h = Estimator.chargeHoursFromCurrent(50.0, 5000.0, 3000.0)
        assertEquals(1.3333, h!!, 0.001)
    }

    @Test
    fun hours_from_current() {
        assertEquals(5.0, Estimator.hoursFromCurrent(2500.0, 500.0)!!, 0.0001)
        assertNull(Estimator.hoursFromCurrent(2500.0, 5.0))
    }

    @Test
    fun wear_projection() {
        // 95% după 12 luni => 0,4167 pe lună => 36 de luni până la 80%
        assertEquals(36.0, Estimator.monthsLeftToLimit(95.0, 12.0)!!, 0.01)
        assertNull(Estimator.monthsLeftToLimit(100.0, 12.0))
        assertNull(Estimator.monthsLeftToLimit(95.0, 0.5))
        assertEquals(0.0, Estimator.monthsLeftToLimit(79.0, 24.0)!!, 0.0)
    }

    @Test
    fun average_discharge_rate() {
        val samples = (0..8).map { i -> s(now + i * hour / 2, 100 - i * 2, 3, 0) } // 2 puncte la 30 min = 4 pe oră
        assertEquals(4.0, Estimator.avgDischargeRate(samples)!!, 0.01)
    }

    @Test
    fun durations_in_romanian() {
        assertEquals("5 h 30 min", Format.duration(5.5))
        assertEquals("45 min", Format.duration(0.75))
        assertEquals("2 z 4 h", Format.duration(52.0))
        assertEquals("peste 7 zile", Format.duration(200.0))
        assertEquals("1 an și 2 luni", Format.months(14.0))
        assertEquals("3 ani", Format.months(36.0))
        assertEquals("1 lună", Format.months(1.0))
        assertEquals("peste 5 ani", Format.months(90.0))
    }
}
