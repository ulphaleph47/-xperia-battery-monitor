package ro.florin.bateriexperia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Grafic simplu cu linie, desenat direct pe Canvas (fără biblioteci externe). */
class ChartView(context: Context) : View(context) {

    var points: List<Pair<Long, Float>> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var lineColor: Int = Color.parseColor("#4FC3F7")
        set(value) {
            field = value
            invalidate()
        }
    var decimals: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    /** Limite fixe pentru axa verticală (de exemplu 0–100 pentru nivelul bateriei). Null = automat. */
    var fixedMin: Float? = null
        set(value) {
            field = value
            invalidate()
        }
    var fixedMax: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        strokeJoin = Paint.Join.ROUND
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0B0B0")
        textSize = 11f * d
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Culorile liniilor de ghidaj și ale textului, potrivite temei curente. */
    fun applyColors(grid: Int, text: Int) {
        gridPaint.color = grid
        textPaint.color = text
        invalidate()
    }
    private val timeFormat = SimpleDateFormat("dd.MM HH:mm", Locale("ro", "RO"))

    /** Pas "rotund" pentru axă (1, 2, 2,5, 5 sau 10 înmulțit cu o putere a lui 10). */
    private fun niceStep(range: Float, ticks: Int): Float {
        val raw = range / ticks
        val mag = Math.pow(10.0, Math.floor(Math.log10(raw.toDouble())))
        val norm = raw / mag
        val s = when {
            norm <= 1.0 -> 1.0
            norm <= 2.0 -> 2.0
            norm <= 2.5 -> 2.5
            norm <= 5.0 -> 5.0
            else -> 10.0
        }
        return (s * mag).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (points.size < 2) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Prea puține măsurători pentru grafic", w / 2, h / 2, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            return
        }

        val left = 48f * d
        val right = w - 10f * d
        val top = 8f * d
        val bottom = h - 24f * d

        var dataMin = points.minOf { it.second }
        var dataMax = points.maxOf { it.second }
        if (dataMax - dataMin < 0.001f) {
            dataMin -= 1f
            dataMax += 1f
        }

        val minV: Float
        val maxV: Float
        val step: Float
        val fMin = fixedMin
        val fMax = fixedMax
        if (fMin != null && fMax != null) {
            minV = fMin
            maxV = fMax
            step = (fMax - fMin) / 4f
        } else {
            step = niceStep(dataMax - dataMin, 4)
            minV = Math.floor((dataMin / step).toDouble()).toFloat() * step
            maxV = Math.ceil((dataMax / step).toDouble()).toFloat() * step
        }
        val ticks = Math.round((maxV - minV) / step).coerceIn(1, 10)

        val t0 = points.first().first
        val t1 = points.last().first
        val dt = max(1L, t1 - t0).toFloat()

        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..ticks) {
            val y = bottom - (bottom - top) * i / ticks.toFloat()
            canvas.drawLine(left, y, right, y, gridPaint)
            val v = minV + (maxV - minV) * i / ticks.toFloat()
            canvas.drawText(String.format(Locale("ro", "RO"), "%.${decimals}f", v), left - 4f * d, y + 4f * d, textPaint)
        }

        // Dacă sunt foarte multe puncte, se desenează doar fiecare al n-lea.
        val step = max(1, points.size / 1200)
        val path = Path()
        var first = true
        var idx = 0
        while (idx < points.size) {
            val p = points[idx]
            val x = left + (right - left) * (p.first - t0) / dt
            val y = bottom - (bottom - top) * (p.second - minV) / (maxV - minV)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            idx += step
        }
        linePaint.color = lineColor
        canvas.drawPath(path, linePaint)

        val last = points.last()
        dotPaint.color = lineColor
        canvas.drawCircle(
            right,
            bottom - (bottom - top) * (last.second - minV) / (maxV - minV),
            3.5f * d,
            dotPaint
        )

        val yText = h - 6f * d
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(timeFormat.format(Date(t0)), left, yText, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(timeFormat.format(Date((t0 + t1) / 2)), (left + right) / 2, yText, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeFormat.format(Date(t1)), right, yText, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }
}
