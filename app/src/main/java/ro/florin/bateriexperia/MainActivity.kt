package ro.florin.bateriexperia

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private val ro = Locale("ro", "RO")
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var store: SampleStore
    private lateinit var prefs: SharedPreferences

    private lateinit var bigLevel: TextView
    private lateinit var bigStatus: TextView
    private lateinit var rowsBox: LinearLayout
    private lateinit var chart: ChartView
    private lateinit var chartTitle: TextView
    private lateinit var historyInfo: TextView
    private lateinit var designInput: EditText

    private val rowViews = LinkedHashMap<String, TextView>()
    private val seriesButtons = ArrayList<Button>()
    private val rangeButtons = ArrayList<Button>()

    private var series = 0
    private var rangeIdx = 0
    private var lastLogged = 0L
    private var lastHistory = 0L
    private var medianCap = Float.NaN
    private var medianCount = 0
    private var cachedSamples: List<Sample> = emptyList()
    private var lastReading: Reading? = null

    private val seriesNames = listOf("Nivel", "Temperatură", "Tensiune", "Capacitate")
    private val rangeNames = listOf("24 h", "7 zile", "30 zile")
    private val rangeHours = listOf(24L, 24L * 7, 24L * 30)

    private val bgColor = Color.parseColor("#121212")
    private val cardColor = Color.parseColor("#1E1E1E")
    private val accent = Color.parseColor("#4FC3F7")
    private val chipOff = Color.parseColor("#2C2C2C")
    private val textMain = Color.parseColor("#F2F2F2")
    private val textDim = Color.parseColor("#9E9E9E")

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SampleStore(this)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        lastLogged = prefs.getLong("last_log", 0L)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        LogJobService.schedule(this)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bgColor)
        scroll.clipToPadding = false
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(24))
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        bigLevel = TextView(this)
        bigLevel.setTextColor(textMain)
        bigLevel.textSize = 56f
        bigLevel.setTypeface(null, Typeface.BOLD)
        bigLevel.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(bigLevel, matchWrap())

        bigStatus = TextView(this)
        bigStatus.setTextColor(textDim)
        bigStatus.textSize = 16f
        bigStatus.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(bigStatus, matchWrap(bottom = 16))

        // --- cardul cu valori
        col.addView(sectionTitle("Valori în timp real"), matchWrap(top = 8, bottom = 6))
        rowsBox = card()
        col.addView(rowsBox, matchWrap())

        // --- grafic
        col.addView(sectionTitle("Istoric"), matchWrap(top = 20, bottom = 6))
        val chartCard = card()
        chartTitle = TextView(this)
        chartTitle.setTextColor(textMain)
        chartTitle.textSize = 14f
        chartCard.addView(chartTitle, matchWrap(bottom = 8))

        val seriesRow = LinearLayout(this)
        seriesRow.orientation = LinearLayout.HORIZONTAL
        for (i in seriesNames.indices) {
            val b = chip(seriesNames[i]) {
                series = i
                updateChips()
                updateChart()
            }
            seriesButtons.add(b)
            seriesRow.addView(b, chipParams())
        }
        chartCard.addView(seriesRow, matchWrap(bottom = 6))

        val rangeRow = LinearLayout(this)
        rangeRow.orientation = LinearLayout.HORIZONTAL
        for (i in rangeNames.indices) {
            val b = chip(rangeNames[i]) {
                rangeIdx = i
                updateChips()
                updateChart()
            }
            rangeButtons.add(b)
            rangeRow.addView(b, chipParams())
        }
        chartCard.addView(rangeRow, matchWrap(bottom = 8))

        chart = ChartView(this)
        chartCard.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)))
        col.addView(chartCard, matchWrap())

        historyInfo = TextView(this)
        historyInfo.setTextColor(textDim)
        historyInfo.textSize = 12f
        col.addView(historyInfo, matchWrap(top = 8))

        // --- setări
        col.addView(sectionTitle("Setări"), matchWrap(top = 20, bottom = 6))
        val settings = card()
        val lbl = TextView(this)
        lbl.setTextColor(textMain)
        lbl.textSize = 14f
        lbl.text = "Capacitatea nominală a bateriei (mAh)"
        settings.addView(lbl, matchWrap(bottom = 6))

        val inputRow = LinearLayout(this)
        inputRow.orientation = LinearLayout.HORIZONTAL
        designInput = EditText(this)
        designInput.inputType = InputType.TYPE_CLASS_NUMBER
        designInput.setTextColor(textMain)
        designInput.setText(prefs.getInt("design_mah", 5000).toString())
        inputRow.addView(designInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val save = chip("Salvează") { saveDesignCapacity() }
        inputRow.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        settings.addView(inputRow, matchWrap())

        val note = TextView(this)
        note.setTextColor(textDim)
        note.textSize = 12f
        note.text = "Xperia 1 V: 5000 mAh. Sănătatea estimată se calculează față de această valoare."
        settings.addView(note, matchWrap(top = 6))
        col.addView(settings, matchWrap())

        val clear = chip("Șterge istoricul") { confirmClear() }
        col.addView(clear, matchWrap(top = 16))

        val foot = TextView(this)
        foot.setTextColor(textDim)
        foot.textSize = 12f
        foot.text = "Aplicația salvează o măsurătoare la aproximativ 15 minute, chiar dacă nu e deschisă. " +
            "Datele rămân doar pe telefon. Dacă ai restricționat bateria pentru această aplicație, " +
            "setează-o pe „Nerestricționat” ca înregistrarea să nu fie oprită."
        col.addView(foot, matchWrap(top = 12))

        setContentView(scroll)
        updateChips()
    }

    private fun matchWrap(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top)
        lp.bottomMargin = dp(bottom)
        return lp
    }

    private fun chipParams(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = dp(6)
        return lp
    }

    private fun sectionTitle(t: String): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.setTextColor(accent)
        tv.textSize = 13f
        tv.setTypeface(null, Typeface.BOLD)
        return tv
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(cardColor)
        bg.cornerRadius = dp(12).toFloat()
        c.background = bg
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        return c
    }

    private fun chip(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 12f
        b.minWidth = 0
        b.minimumWidth = 0
        b.setTextColor(textMain)
        b.setPadding(dp(8), 0, dp(8), 0)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun updateChips() {
        for (i in seriesButtons.indices) styleChip(seriesButtons[i], i == series)
        for (i in rangeButtons.indices) styleChip(rangeButtons[i], i == rangeIdx)
    }

    private fun styleChip(b: Button, selected: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(if (selected) Color.parseColor("#0277BD") else chipOff)
        b.background = bg
    }

    private fun setRow(label: String, value: String) {
        val tv = rowViews.getOrPut(label) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(5), 0, dp(5))
            val l = TextView(this)
            l.text = label
            l.setTextColor(textDim)
            l.textSize = 14f
            row.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val v = TextView(this)
            v.setTextColor(textMain)
            v.textSize = 14f
            v.setTypeface(null, Typeface.BOLD)
            v.gravity = Gravity.END
            row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            rowsBox.addView(row, matchWrap())
            v
        }
        tv.text = value
    }

    // ---------------------------------------------------------------- date

    private fun refresh() {
        val r = BatteryReader.read(this)
        lastReading = r
        val now = System.currentTimeMillis()

        if (now - lastLogged > 5 * 60_000L) {
            store.append(r)
            lastLogged = now
            prefs.edit().putLong("last_log", now).apply()
            reloadHistory()
        } else if (now - lastHistory > 30_000L || cachedSamples.isEmpty() && now - lastHistory > 5_000L) {
            reloadHistory()
        }
        render(r)
    }

    private fun reloadHistory() {
        val now = System.currentTimeMillis()
        lastHistory = now
        cachedSamples = store.load(now - 30L * 24 * 3600_000L)

        val twoWeeksAgo = now - 14L * 24 * 3600_000L
        val caps = cachedSamples
            .filter { it.ts >= twoWeeksAgo && it.level in 20..95 && !it.estCapMah.isNaN() }
            .map { it.estCapMah }
            .sorted()
        medianCount = caps.size
        medianCap = if (caps.isEmpty()) Float.NaN else caps[caps.size / 2]
        updateChart()
    }

    private fun designCapacity(): Int = prefs.getInt("design_mah", 5000)

    private fun render(r: Reading) {
        bigLevel.text = "${r.level}%"
        bigStatus.text = statusText(r)

        setRow("Nivel", "${r.level} %")
        setRow("Temperatură", String.format(ro, "%.1f °C", r.tempC))
        setRow("Tensiune", String.format(ro, "%.2f V", r.voltMv / 1000.0))

        if (r.currentUa == NA_LONG) {
            setRow("Curent", "indisponibil")
            setRow("Putere", "indisponibil")
        } else {
            val sign = when (r.status) {
                2 -> "+"
                3 -> "−"
                else -> ""
            }
            setRow("Curent", "$sign${abs(r.currentUa) / 1000} mA")
            setRow("Putere", String.format(ro, "%.1f W", r.powerW))
        }

        setRow("Capacitate rămasă", if (r.chargeUah > 0) "${r.chargeUah / 1000} mAh" else "indisponibil")
        setRow(
            "Capacitate totală (acum)",
            if (r.estCapMah.isNaN()) "indisponibil" else "${r.estCapMah.roundToInt()} mAh"
        )
        setRow(
            "Capacitate totală (mediană)",
            if (medianCap.isNaN()) "se adună date" else "${medianCap.roundToInt()} mAh"
        )

        val design = designCapacity().toFloat()
        val healthText = when {
            !medianCap.isNaN() -> "${(medianCap / design * 100).roundToInt()} % (din $medianCount măsurători)"
            !r.estCapMah.isNaN() -> "${(r.estCapMah / design * 100).roundToInt()} % (valoare momentană)"
            else -> "indisponibil"
        }
        setRow("Sănătate estimată", healthText)
        setRow("Sănătate raportată de sistem", if (r.soh > 0) "${r.soh} %" else "indisponibil")
        setRow("Cicluri de încărcare", if (r.cycles > 0) r.cycles.toString() else "indisponibil")
        setRow("Starea bateriei", healthName(r.health))
        setRow("Politică de încărcare", policyName(r.policy))
        setRow("Data fabricației", dateText(r.mfgSec))
        setRow("Prima utilizare", dateText(r.firstUseSec))
        setRow("Tehnologie", if (r.tech.isEmpty()) "indisponibil" else r.tech)
    }

    private fun updateChart() {
        val now = System.currentTimeMillis()
        val since = now - rangeHours[rangeIdx] * 3600_000L
        val pts = ArrayList<Pair<Long, Float>>()
        for (s in cachedSamples) {
            if (s.ts < since) continue
            val v: Float? = when (series) {
                0 -> s.level.toFloat()
                1 -> s.tempC
                2 -> s.voltMv / 1000f
                else -> if (s.level >= 20 && !s.estCapMah.isNaN()) s.estCapMah else null
            }
            if (v != null) pts.add(Pair(s.ts, v))
        }
        chart.points = pts
        when (series) {
            0 -> { chart.lineColor = Color.parseColor("#4FC3F7"); chart.decimals = 0; chartTitle.text = "Nivel baterie (%)" }
            1 -> { chart.lineColor = Color.parseColor("#FFB74D"); chart.decimals = 1; chartTitle.text = "Temperatură (°C)" }
            2 -> { chart.lineColor = Color.parseColor("#81C784"); chart.decimals = 2; chartTitle.text = "Tensiune (V)" }
            else -> { chart.lineColor = Color.parseColor("#BA68C8"); chart.decimals = 0; chartTitle.text = "Capacitate totală estimată (mAh)" }
        }
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", ro)
        historyInfo.text = if (cachedSamples.isEmpty()) {
            "Nicio măsurătoare salvată încă."
        } else {
            "${cachedSamples.size} măsurători în ultimele 30 de zile, ultima la ${fmt.format(Date(cachedSamples.last().ts))}."
        }
    }

    // ---------------------------------------------------------------- acțiuni

    private fun saveDesignCapacity() {
        val v = designInput.text.toString().trim().toIntOrNull()
        if (v == null || v < 1000 || v > 10000) {
            Toast.makeText(this, "Introdu o valoare între 1000 și 10000 mAh", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putInt("design_mah", v).apply()
        Toast.makeText(this, "Salvat", Toast.LENGTH_SHORT).show()
        lastReading?.let { render(it) }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Ștergi istoricul?")
            .setMessage("Toate măsurătorile salvate vor fi șterse de pe telefon.")
            .setPositiveButton("Șterge") { _, _ ->
                store.clear()
                cachedSamples = emptyList()
                medianCap = Float.NaN
                medianCount = 0
                updateChart()
                lastReading?.let { render(it) }
            }
            .setNegativeButton("Anulează", null)
            .show()
    }

    // ---------------------------------------------------------------- texte

    private fun statusText(r: Reading): String {
        val st = when (r.status) {
            2 -> "Se încarcă"
            3 -> "Se descarcă"
            4 -> "Nu se încarcă"
            5 -> "Încărcată complet"
            else -> "Stare necunoscută"
        }
        val src = when (r.plugged) {
            1 -> "încărcător AC"
            2 -> "USB"
            4 -> "wireless"
            8 -> "dock"
            else -> ""
        }
        return if (src.isEmpty()) st else "$st · $src"
    }

    private fun healthName(h: Int): String = when (h) {
        2 -> "Bună"
        3 -> "Supraîncălzită"
        4 -> "Defectă"
        5 -> "Supratensiune"
        6 -> "Defect nespecificat"
        7 -> "Prea rece"
        else -> "Necunoscută"
    }

    private fun policyName(p: Int): String = when (p) {
        1 -> "Standard"
        2, 3 -> "Adaptivă"
        4 -> "Limitată (viață lungă)"
        -1 -> "indisponibil"
        else -> "cod $p"
    }

    private fun dateText(sec: Long): String {
        if (sec <= 0) return "indisponibil"
        return SimpleDateFormat("dd.MM.yyyy", ro).format(Date(sec * 1000L))
    }
}
