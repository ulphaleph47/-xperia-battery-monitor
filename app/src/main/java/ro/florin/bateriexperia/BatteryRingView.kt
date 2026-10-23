package ro.florin.bateriexperia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Inelul mare cu nivelul bateriei, din ecranul principal. */
class BatteryRingView(context: Context) : View(context) {

    var level: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }
    var ringColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var trackColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var textColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var subTextColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var subText: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val sp = d * resources.configuration.fontScale
    private val strokeW = 14f * d
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokeW
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 56f * sp
        isFakeBoldText = true
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * sp
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = resolveSize((220f * d).toInt(), widthMeasureSpec)
        val s = minOf(size, (240f * d).toInt())
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = strokeW / 2f + 2f * d
        rect.set(half, half, width - half, height - half)

        ringPaint.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, ringPaint)

        if (level > 0) {
            ringPaint.color = ringColor
            canvas.drawArc(rect, -90f, 360f * level / 100f, false, ringPaint)
        }

        val cx = width / 2f
        val cy = height / 2f
        bigPaint.color = textColor
        canvas.drawText("$level%", cx, cy + bigPaint.textSize * 0.30f, bigPaint)

        if (subText.isNotEmpty()) {
            smallPaint.color = subTextColor
            canvas.drawText(subText, cx, cy + bigPaint.textSize * 0.30f + 26f * d, smallPaint)
        }
    }
}
