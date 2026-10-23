package ro.florin.bateriexperia

import android.graphics.Color

/** Culorile aplicației, pentru tema închisă și cea deschisă. */
class Palette(val night: Boolean) {
    val bg = c(if (night) "#0F1115" else "#F2F4F7")
    val card = c(if (night) "#1A1D23" else "#FFFFFF")
    val nav = c(if (night) "#14171C" else "#FFFFFF")
    val text = c(if (night) "#ECEFF4" else "#14181D")
    val dim = c(if (night) "#8B93A1" else "#5F6B7A")
    val accent = c(if (night) "#4FC3F7" else "#0277BD")
    val chipOff = c(if (night) "#262B33" else "#E6EAF0")
    val chipOn = c(if (night) "#0B6FA8" else "#0277BD")
    val divider = c(if (night) "#2A2F38" else "#E3E7ED")
    val track = c(if (night) "#2A2F38" else "#E3E7ED")

    val good = c(if (night) "#66BB6A" else "#2E7D32")
    val warn = c(if (night) "#FFB74D" else "#EF6C00")
    val bad = c(if (night) "#EF5350" else "#C62828")
    val charging = c(if (night) "#4FC3F7" else "#0277BD")

    private fun c(hex: String): Int = Color.parseColor(hex)
}
