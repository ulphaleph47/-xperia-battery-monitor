package ro.florin.bateriexperia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Bară orizontală de progres, cu margini rotunjite. */
class BarView(context: Context) : View(context) {

    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var barColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var trackColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = height / 2f
        paint.color = trackColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, paint)
        if (fraction > 0f) {
            paint.color = barColor
            rect.set(0f, 0f, maxOf(width * fraction, height.toFloat()), height.toFloat())
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }
}
