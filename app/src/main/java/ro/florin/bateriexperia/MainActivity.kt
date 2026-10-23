package ro.florin.bateriexperia

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private val ro = Locale("ro", "RO")
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var store: SampleStore
    private lateinit var prefs: SharedPreferences
    private lateinit var pal: Palette

    // structura generală
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private val pages = ArrayList<View>()
    private val navIcons = ArrayList<ImageView>()
    private val navLabels = ArrayList<TextView>()
    private var tab = 0

    // ecranul Acasă
    private lateinit var ring: BatteryRingView
    private lateinit var statusLine: TextView
    private lateinit var timeCaption: TextView
    private lateinit var timeValue: TextView
    private lateinit var timeNote: TextView
    private lateinit var tileTemp: TextView
    private lateinit var tilePower: TextView
    private lateinit var tileCurrent: TextView
    private lateinit var tileVolt: TextView
    private lateinit var lifeHealth: TextView
    private lateinit var lifeBar: BarView
    private lateinit var lifeText: TextView
    private lateinit var lifeNote: TextView
    private lateinit var careCard: LinearLayout
    private lateinit var careText: TextView

    // ecranul Istoric
    private lateinit var chart: ChartView
    private lateinit var chartTitle: TextView
    private lateinit var historyInfo: TextView
    private val seriesButtons = ArrayList<Button>()
    private val rangeButtons = ArrayList<Button>()
    private var series = 0
    private var rangeIdx = 0

    // ecranul Setări
    private lateinit var designInput: EditText
    private lateinit var purchaseInput: EditText

    // detalii
    private val groups = LinkedHashMap<String, LinearLayout>()
    private val rowViews = LinkedHashMap<String, TextView>()

    // date
    private var lastLogged = 0L
    private var lastHistory = 0L
    private var cachedSamples: List<Sample> = emptyList()
    private var medianCap = Float.NaN
    private var medianCount = 0
    private var lastReading: Reading? = null
    private val currentBuf = ArrayList<Double>()

    private val seriesNames = listOf("Nivel", "Temp.", "Tensiune", "Capacitate")
    private val rangeNames = listOf("24 de ore", "7 zile", "30 de zile")
    private val rangeHours = listOf(24L, 24L * 7, 24L * 30)

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000)
        }
    }

    // ---------------------------------------------------------------- ciclul de viață

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SampleStore(this)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        lastLogged = prefs.getLong("last_log", 0L)

        val nightMode = if (Build.VERSION.SDK_INT >= 29) {
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } else {
            true
        }
        pal = Palette(nightMode)

        tab = intent?.getIntExtra("tab", -1)?.takeIf { it in 0..3 }
            ?: savedInstanceState?.getInt("tab", 0)
            ?: 0
        series = intent?.getIntExtra("series", 0)?.takeIf { it in 0..3 } ?: 0
        rangeIdx = intent?.getIntExtra("range", 0)?.takeIf { it in 0..2 } ?: 0
        buildUi()
        selectTab(tab)

        // Pictogramele din bara de stare și din bara de navigare: închise pe tema deschisă, deschise pe cea închisă.
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (nightMode) 0 else mask, mask)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
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

    // ---------------------------------------------------------------- construirea interfeței

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun buildUi() {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(pal.bg)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val line = View(this)
        line.setBackgroundColor(pal.divider)
        root.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(pal.nav)
        root.addView(nav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val titles = listOf("Acasă", "Detalii", "Istoric", "Setări")
        val icons = listOf(R.drawable.ic_nav_home, R.drawable.ic_nav_details, R.drawable.ic_nav_history, R.drawable.ic_nav_settings)
        for (i in 0..3) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(0, dp(10), 0, dp(10))
            item.contentDescription = titles[i]
            val icon = ImageView(this)
            icon.setImageResource(icons[i])
            item.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
            val label = TextView(this)
            label.text = titles[i]
            label.textSize = 12f
            label.gravity = Gravity.CENTER
            item.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4) })
            item.setOnClickListener { selectTab(i) }
            nav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            navIcons.add(icon)
            navLabels.add(label)
        }

        val homePage = buildHome()
        val detailsPage = buildDetails()
        val historyPage = buildHistory()
        val settingsPage = buildSettings()
        for (p in listOf(homePage, detailsPage, historyPage, settingsPage)) {
            pages.add(p)
            content.addView(p, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        root.setOnApplyWindowInsetsListener { _, insets ->
            var l = 0
            var t = 0
            var r = 0
            var b = 0
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                l = bars.left
                t = bars.top
                r = bars.right
                b = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                run {
                    l = insets.systemWindowInsetLeft
                    t = insets.systemWindowInsetTop
                    r = insets.systemWindowInsetRight
                    b = insets.systemWindowInsetBottom
                }
            }
            content.setPadding(l, t, r, 0)
            nav.setPadding(l, 0, r, b)
            insets
        }

        setContentView(root)
    }

    /** Pagină derulabilă cu titlu; întoarce coloana în care se adaugă conținutul. */
    private fun newPage(title: String): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(pal.bg)
        scroll.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(16), dp(20), dp(28))
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val t = TextView(this)
        t.text = title
        t.setTextColor(pal.text)
        t.textSize = 30f
        t.setTypeface(null, Typeface.BOLD)
        col.addView(t, lp(bottom = 16))
        return Pair(scroll, col)
    }

    private fun buildHome(): View {
        val (scroll, col) = newPage("Baterie")

        // --- nivelul și starea
        val hero = card()
        hero.gravity = Gravity.CENTER_HORIZONTAL
        ring = BatteryRingView(this)
        ring.trackColor = pal.track
        ring.textColor = pal.text
        ring.subTextColor = pal.dim
        ring.ringColor = pal.good
        ring.subText = "nivel actual"
        hero.addView(ring, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        statusLine = text(16f, pal.dim)
        statusLine.gravity = Gravity.CENTER_HORIZONTAL
        hero.addView(statusLine, lp(top = 12))
        col.addView(hero, lp(bottom = 16))

        // --- timp rămas
        val timeCard = card()
        timeCaption = text(13f, pal.dim)
        timeValue = text(32f, pal.text, bold = true)
        timeNote = text(13f, pal.dim)
        timeCard.addView(timeCaption, lp())
        timeCard.addView(timeValue, lp(top = 4))
        timeCard.addView(timeNote, lp(top = 6))
        col.addView(timeCard, lp(bottom = 16))

        // --- Battery Care (apare doar când e cazul)
        careCard = card()
        careText = text(14f, pal.text)
        careCard.addView(careText, lp())
        careCard.visibility = View.GONE
        col.addView(careCard, lp(bottom = 16))

        // --- cele patru valori
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        tileTemp = addTile(row1, "Temperatură")
        tilePower = addTile(row1, "Putere")
        tileCurrent = addTile(row2, "Curent")
        tileVolt = addTile(row2, "Tensiune")
        col.addView(row1, lp(bottom = 12))
        col.addView(row2, lp(bottom = 16))

        // --- durata de viață
        val life = card()
        val lifeTitle = text(13f, pal.dim)
        lifeTitle.text = "Durata de viață a bateriei"
        life.addView(lifeTitle, lp())
        lifeHealth = text(32f, pal.text, bold = true)
        life.addView(lifeHealth, lp(top = 4))
        val sub = text(13f, pal.dim)
        sub.text = "sănătate estimată (capacitate față de cea nominală)"
        life.addView(sub, lp())
        lifeBar = BarView(this)
        lifeBar.barColor = pal.good
        lifeBar.trackColor = pal.track
        life.addView(lifeBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).also { it.topMargin = dp(12) })
        lifeText = text(15f, pal.text)
        life.addView(lifeText, lp(top = 14))
        lifeNote = text(12f, pal.dim)
        life.addView(lifeNote, lp(top = 8))
        col.addView(life, lp())

        return scroll
    }

    private fun buildDetails(): View {
        val (scroll, col) = newPage("Detalii")
        for (g in listOf("Energie", "Capacitate și sănătate", "Informații despre baterie")) {
            col.addView(groupCard(g), lp(bottom = 16))
        }
        return scroll
    }

    private fun buildHistory(): View {
        val (scroll, col) = newPage("Istoric")

        val chartCard = card()
        chartTitle = text(15f, pal.text, bold = true)
        chartCard.addView(chartTitle, lp(bottom = 12))

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
        chartCard.addView(seriesRow, lp(bottom = 8))

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
        chartCard.addView(rangeRow, lp(bottom = 12))

        chart = ChartView(this)
        chart.applyColors(pal.divider, pal.dim)
        chartCard.addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)))
        col.addView(chartCard, lp(bottom = 16))

        col.addView(groupCard("Statistici"), lp(bottom = 12))

        historyInfo = text(12f, pal.dim)
        col.addView(historyInfo, lp())

        updateChips()
        return scroll
    }

    private fun buildSettings(): View {
        val (scroll, col) = newPage("Setări")

        val phone = card()
        val l1 = text(15f, pal.text, bold = true)
        l1.text = "Telefonul tău"
        phone.addView(l1, lp(bottom = 12))

        val a = text(13f, pal.dim)
        a.text = "Capacitatea nominală a bateriei (mAh)"
        phone.addView(a, lp())
        designInput = input(InputType.TYPE_CLASS_NUMBER, prefs.getInt("design_mah", 5000).toString())
        phone.addView(designInput, lp(top = 6, bottom = 4))
        val a2 = text(12f, pal.dim)
        a2.text = "Xperia 1 V: 5000 mAh. Sănătatea bateriei se calculează față de această valoare."
        phone.addView(a2, lp(bottom = 14))

        val b = text(13f, pal.dim)
        b.text = "Luna și anul achiziției (opțional, de exemplu 07.2023)"
        phone.addView(b, lp())
        purchaseInput = input(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE, prefs.getString("purchase", "") ?: "")
        purchaseInput.hint = "LL.AAAA"
        purchaseInput.setHintTextColor(pal.dim)
        phone.addView(purchaseInput, lp(top = 6, bottom = 4))
        val b2 = text(12f, pal.dim)
        b2.text = "Ajută la estimarea mai exactă a duratei de viață a bateriei."
        phone.addView(b2, lp(bottom = 14))

        val save = chip("Salvează") { saveSettings() }
        styleChip(save, true)
        phone.addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        col.addView(phone, lp(bottom = 16))

        val tools = card()
        val l2 = text(15f, pal.text, bold = true)
        l2.text = "Baterie și înregistrare"
        tools.addView(l2, lp(bottom = 12))

        val care = chip("Deschide setările bateriei (Battery Care)") {
            openSafely(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        }
        tools.addView(care, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        val opt = chip("Nerestricționează aplicația (recomandat)") {
            openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        tools.addView(opt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).also { it.topMargin = dp(8) })
        val hint = text(12f, pal.dim)
        hint.text = "Aplicația salvează o măsurătoare la aproximativ 15 minute, chiar dacă nu e deschisă. " +
            "Dacă Android îi restricționează bateria, înregistrarea poate fi oprită."
        tools.addView(hint, lp(top = 10))
        col.addView(tools, lp(bottom = 16))

        val data = card()
        val l3 = text(15f, pal.text, bold = true)
        l3.text = "Datele tale"
        data.addView(l3, lp(bottom = 8))
        val d1 = text(13f, pal.dim)
        d1.text = "Istoricul rămâne doar pe telefon. Aplicația nu folosește internetul."
        data.addView(d1, lp(bottom = 12))
        val clear = chip("Șterge istoricul") { confirmClear() }
        data.addView(clear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        col.addView(data, lp())

        return scroll
    }

    // ---------------------------------------------------------------- piese de interfață

    private fun lp(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun text(size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(pal.card)
        bg.cornerRadius = dp(20).toFloat()
        c.background = bg
        c.setPadding(dp(18), dp(16), dp(18), dp(16))
        return c
    }

    private fun addTile(row: LinearLayout, title: String): TextView {
        val tile = card()
        val tl = text(12f, pal.dim)
        tl.text = title
        tile.addView(tl, lp())
        val value = text(20f, pal.text, bold = true)
        tile.addView(value, lp(top = 4))
        val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        if (row.childCount == 0) p.marginEnd = dp(6) else p.marginStart = dp(6)
        row.addView(tile, p)
        return value
    }

    private fun groupCard(name: String): LinearLayout {
        val c = card()
        val title = text(13f, pal.accent, bold = true)
        title.text = name
        c.addView(title, lp(bottom = 6))
        groups[name] = c
        return c
    }

    private fun input(type: Int, value: String): EditText {
        val e = EditText(this)
        e.inputType = type
        e.setText(value)
        e.setTextColor(pal.text)
        e.textSize = 16f
        e.setSingleLine(true)
        val bg = GradientDrawable()
        bg.setColor(pal.chipOff)
        bg.cornerRadius = dp(12).toFloat()
        e.background = bg
        e.setPadding(dp(14), dp(10), dp(14), dp(10))
        return e
    }

    private fun chip(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 12f
        b.maxLines = 1
        b.setSingleLine(true)
        b.minWidth = 0
        b.minimumWidth = 0
        b.minHeight = 0
        b.minimumHeight = 0
        b.stateListAnimator = null
        b.setTextColor(pal.text)
        b.setPadding(dp(4), dp(8), dp(4), dp(8))
        b.setOnClickListener { onClick() }
        styleChip(b, false)
        return b
    }

    private fun chipParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, dp(38), 1f)
        p.marginEnd = dp(6)
        return p
    }

    private fun styleChip(b: Button, selected: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(19).toFloat()
        bg.setColor(if (selected) pal.chipOn else pal.chipOff)
        b.background = bg
        b.setTextColor(if (selected) Color.WHITE else pal.text)
    }

    private fun updateChips() {
        for (i in seriesButtons.indices) styleChip(seriesButtons[i], i == series)
        for (i in rangeButtons.indices) styleChip(rangeButtons[i], i == rangeIdx)
    }

    private fun selectTab(i: Int) {
        tab = i
        for (k in pages.indices) {
            pages[k].visibility = if (k == i) View.VISIBLE else View.GONE
            val on = k == i
            navIcons[k].setColorFilter(if (on) pal.accent else pal.dim)
            navLabels[k].setTextColor(if (on) pal.accent else pal.dim)
            navLabels[k].setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun setRow(group: String, label: String, value: String) {
        val key = "$group|$label"
        val tv = rowViews.getOrPut(key) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(7), 0, dp(7))
            val l = text(14f, pal.dim)
            l.text = label
            row.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val v = text(14f, pal.text, bold = true)
            v.gravity = Gravity.END
            row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            groups[group]?.addView(row, lp())
            v
        }
        tv.text = value
    }

    // ---------------------------------------------------------------- date și calcule

    private fun refresh() {
        val r = BatteryReader.read(this)
        lastReading = r
        val now = System.currentTimeMillis()

        if (r.currentUa != NA_LONG) {
            currentBuf.add(abs(r.currentUa) / 1000.0)
            if (currentBuf.size > 20) currentBuf.removeAt(0)
        }

        if (now - lastLogged > 5 * 60_000L) {
            store.append(r)
            lastLogged = now
            prefs.edit().putLong("last_log", now).apply()
            reloadHistory()
        } else if (now - lastHistory > 30_000L || (cachedSamples.isEmpty() && now - lastHistory > 5_000L)) {
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
            .filter { it.ts >= twoWeeksAgo && it.level in 20..95 && capOk(it.estCapMah) }
            .map { it.estCapMah }
        medianCount = caps.size
        medianCap = Estimator.median(caps)
        updateChart()
    }

    private fun nominalMah(): Int = prefs.getInt("design_mah", 5000)

    /** O capacitate estimată e luată în seamă doar dacă e apropiată de cea nominală (altfel contorul e nesigur). */
    private fun capOk(cap: Float): Boolean {
        if (cap.isNaN()) return false
        val n = nominalMah()
        return cap >= n * 0.5f && cap <= n * 1.5f
    }

    private fun monthsInUse(r: Reading): Double? {
        val now = System.currentTimeMillis()
        val purchase = parsePurchase(prefs.getString("purchase", "") ?: "")
        val start: Long? = when {
            purchase != null -> purchase
            r.firstUseSec > 0 && r.firstUseSec * 1000L > 1_400_000_000_000L -> r.firstUseSec * 1000L
            r.mfgSec > 0 && r.mfgSec * 1000L > 1_400_000_000_000L -> r.mfgSec * 1000L
            else -> null
        }
        if (start == null || start > now) return null
        return (now - start) / (30.44 * 24 * 3600_000.0)
    }

    private fun parsePurchase(s: String): Long? {
        val m = Regex("""^\s*(\d{1,2})[./](\d{4})\s*$""").find(s) ?: return null
        val month = m.groupValues[1].toInt()
        val year = m.groupValues[2].toInt()
        if (month !in 1..12 || year < 2020) return null
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1)
        return cal.timeInMillis
    }

    private fun render(r: Reading) {
        val now = System.currentTimeMillis()
        val nominal = nominalMah().toDouble()
        val chargeOk = capOk(r.estCapMah)
        val capMah: Double = when {
            !medianCap.isNaN() -> medianCap.toDouble()
            chargeOk -> r.estCapMah.toDouble()
            else -> nominal
        }
        val avgCurrentMa: Double? = if (currentBuf.isEmpty()) null else currentBuf.average()
        val charging = r.status == 2
        val full = r.status == 5 || (r.level >= 100 && r.plugged != 0)
        val pluggedNotCharging = r.plugged != 0 && !charging && !full
        val levelPct = r.level.toDouble()
        val currentPct = if (chargeOk && capMah > 0) {
            Estimator.clamp(r.chargeUah / 1000.0 / capMah * 100.0, levelPct - 1.0, levelPct + 1.0)
        } else {
            levelPct
        }

        // ---------------- inel și stare
        ring.level = r.level
        ring.ringColor = when {
            charging -> pal.charging
            r.level < 20 -> pal.bad
            r.level < 50 -> pal.warn
            else -> pal.good
        }
        statusLine.text = statusText(r)

        // ---------------- timp rămas
        when {
            charging -> {
                timeCaption.text = "Timp estimat până la 100%"
                val byHistory = Estimator.chargeHoursLeft(
                    levelPct, Estimator.segmentRatePerHour(cachedSamples, now, true, currentPct, capMah)
                )
                val byCurrent = avgCurrentMa?.let { Estimator.chargeHoursFromCurrent(levelPct, capMah, it) }
                val h = byCurrent ?: byHistory
                if (h != null) {
                    timeValue.text = Format.duration(h)
                    timeNote.text = "Orientativ. După 80% încărcarea încetinește."
                } else {
                    timeValue.text = "Se calculează…"
                    timeNote.text = "Estimarea apare după câteva minute de încărcare."
                }
            }
            full -> {
                timeCaption.text = "Încărcare"
                timeValue.text = "Completă"
                timeNote.text = "Poți deconecta încărcătorul."
            }
            pluggedNotCharging -> {
                timeCaption.text = "Încărcare"
                timeValue.text = "În pauză"
                timeNote.text = "Telefonul e conectat, dar nu încarcă acum."
            }
            else -> {
                timeCaption.text = "Timp rămas estimat"
                val rate = Estimator.segmentRatePerHour(cachedSamples, now, false, currentPct, capMah)
                val byHistory = Estimator.dischargeHoursLeft(currentPct, rate)
                val remainingMah = if (chargeOk) r.chargeUah / 1000.0 else capMah * levelPct / 100.0
                val byCurrent = avgCurrentMa?.let { Estimator.hoursFromCurrent(remainingMah, it) }
                if (byHistory != null) {
                    timeValue.text = Format.duration(byHistory)
                    timeNote.text = if (byCurrent != null) {
                        "până la 0%, după ritmul din ultimele ore. La consumul de acum: ${Format.duration(byCurrent)}."
                    } else {
                        "până la 0%, după ritmul din ultimele ore."
                    }
                } else if (byCurrent != null) {
                    timeValue.text = Format.duration(byCurrent)
                    timeNote.text = "până la 0%, la consumul din acest moment. Estimarea se rafinează după o oră de utilizare."
                } else {
                    timeValue.text = "Se calculează…"
                    timeNote.text = "Estimarea apare după aproximativ o jumătate de oră de utilizare."
                }
            }
        }

        // ---------------- Battery Care
        val careLikely = pluggedNotCharging && r.level in 78..92 && r.tempC < 40f
        if (careLikely) {
            careCard.visibility = View.VISIBLE
            careText.text = "Încărcarea s-a oprit la ${r.level}%. Pe Xperia 1 V asta se întâmplă de obicei când " +
                "funcția Battery Care limitează încărcarea la 80% sau 90% sau o încetinește pentru noapte."
        } else if (pluggedNotCharging && r.tempC >= 40f) {
            careCard.visibility = View.VISIBLE
            careText.text = "Încărcarea e oprită, iar bateria e caldă (${String.format(ro, "%.1f", r.tempC)} °C). " +
                "Telefonul își protejează bateria și reia încărcarea după ce se răcește."
        } else {
            careCard.visibility = View.GONE
        }

        // ---------------- cele patru valori
        tileTemp.text = String.format(ro, "%.1f °C", r.tempC)
        tileTemp.setTextColor(if (r.tempC >= 42f) pal.bad else if (r.tempC >= 38f) pal.warn else pal.text)
        tileVolt.text = String.format(ro, "%.2f V", r.voltMv / 1000.0)
        if (r.currentUa == NA_LONG) {
            tileCurrent.text = "indisponibil"
            tilePower.text = "indisponibil"
        } else {
            val sign = when (r.status) {
                2 -> "+"
                3 -> "−"
                else -> ""
            }
            tileCurrent.text = "$sign${abs(r.currentUa) / 1000} mA"
            tilePower.text = String.format(ro, "%.1f W", r.powerW)
        }

        // ---------------- durata de viață
        val health: Double? = when {
            !medianCap.isNaN() -> Estimator.healthPct(medianCap.toDouble(), nominal)
            chargeOk -> Estimator.healthPct(r.estCapMah.toDouble(), nominal)
            else -> null
        }
        if (health == null) {
            lifeHealth.text = "—"
            lifeBar.fraction = 0f
            lifeText.text = "Se adună date. Estimarea apare după câteva zile de utilizare și încărcări."
        } else {
            lifeHealth.text = "${health.roundToInt()}%"
            lifeBar.fraction = (health / 100.0).toFloat()
            lifeBar.barColor = when {
                health >= 85 -> pal.good
                health >= 80 -> pal.warn
                else -> pal.bad
            }
            val monthsLeft = Estimator.monthsLeftToLimit(health, monthsInUse(r))
                ?: Estimator.trendMonthsLeft(cachedSamples, nominal)
            lifeText.text = when {
                health <= Estimator.WEAR_LIMIT_PCT ->
                    "Capacitatea a coborât sub 80% din cea nominală."
                monthsLeft != null ->
                    "Încă aproximativ ${Format.months(monthsLeft)} până la 80% din capacitatea nominală."
                health >= 100.0 ->
                    "Capacitatea măsurată e la nivelul nominal. Uzura nu se poate estima încă."
                else ->
                    "Pentru o estimare a duratei rămase, introdu luna achiziției în Setări sau lasă aplicația să adune date."
            }
        }
        val cyc = if (r.cycles > 0) "Cicluri de încărcare: ${r.cycles}. " else ""
        lifeNote.text = cyc + "Estimare orientativă, din capacitatea măsurată. Sony indică peste 80% din capacitate după 3 ani de utilizare."

        renderDetails(r, health)
    }

    private fun renderDetails(r: Reading, health: Double?) {
        val g1 = "Energie"
        setRow(g1, "Nivel", "${r.level} %")
        setRow(g1, "Stare", statusText(r))
        setRow(g1, "Temperatură", String.format(ro, "%.1f °C", r.tempC))
        setRow(g1, "Tensiune", String.format(ro, "%.2f V", r.voltMv / 1000.0))
        if (r.currentUa == NA_LONG) {
            setRow(g1, "Curent", "indisponibil")
            setRow(g1, "Putere", "indisponibil")
        } else {
            val sign = when (r.status) {
                2 -> "+"
                3 -> "−"
                else -> ""
            }
            setRow(g1, "Curent", "$sign${abs(r.currentUa) / 1000} mA")
            setRow(g1, "Putere", String.format(ro, "%.1f W", r.powerW))
        }

        val g2 = "Capacitate și sănătate"
        setRow(g2, "Capacitate nominală", "${nominalMah()} mAh")
        val counterOk = capOk(r.estCapMah)
        setRow(g2, "Capacitate rămasă", if (counterOk) "${r.chargeUah / 1000} mAh" else "indisponibil")
        setRow(g2, "Capacitate totală (acum)", if (counterOk) "${r.estCapMah.roundToInt()} mAh" else "indisponibil")
        setRow(g2, "Capacitate totală (mediană)", if (medianCap.isNaN()) "se adună date" else "${medianCap.roundToInt()} mAh")
        setRow(
            g2, "Sănătate estimată",
            when {
                health == null -> "indisponibil"
                !medianCap.isNaN() -> "${health.roundToInt()} % (din $medianCount măsurători)"
                else -> "${health.roundToInt()} % (valoare momentană)"
            }
        )
        setRow(g2, "Sănătate raportată de sistem", if (r.soh > 0) "${r.soh} %" else "indisponibil")

        val g3 = "Informații despre baterie"
        setRow(g3, "Cicluri de încărcare", if (r.cycles > 0) r.cycles.toString() else "indisponibil")
        setRow(g3, "Starea bateriei", healthName(r.health))
        setRow(g3, "Politică de încărcare", policyName(r.policy))
        setRow(g3, "Data fabricației", dateText(r.mfgSec))
        setRow(g3, "Prima utilizare", dateText(r.firstUseSec))
        setRow(g3, "Tehnologie", if (r.tech.isEmpty()) "indisponibil" else r.tech)
    }

    private fun updateChart() {
        val now = System.currentTimeMillis()
        val since = now - rangeHours[rangeIdx] * 3600_000L
        val inRange = cachedSamples.filter { it.ts >= since }
        val pts = ArrayList<Pair<Long, Float>>()
        for (s in inRange) {
            val v: Float? = when (series) {
                0 -> s.level.toFloat()
                1 -> s.tempC
                2 -> s.voltMv / 1000f
                else -> if (s.level >= 20 && capOk(s.estCapMah)) s.estCapMah else null
            }
            if (v != null) pts.add(Pair(s.ts, v))
        }
        chart.fixedMin = if (series == 0) 0f else null
        chart.fixedMax = if (series == 0) 100f else null
        chart.points = pts
        when (series) {
            0 -> { chart.lineColor = pal.accent; chart.decimals = 0; chartTitle.text = "Nivelul bateriei (%)" }
            1 -> { chart.lineColor = pal.warn; chart.decimals = 1; chartTitle.text = "Temperatura (°C)" }
            2 -> { chart.lineColor = pal.good; chart.decimals = 2; chartTitle.text = "Tensiunea (V)" }
            else -> { chart.lineColor = Color.parseColor("#BA68C8"); chart.decimals = 0; chartTitle.text = "Capacitatea totală estimată (mAh)" }
        }

        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", ro)
        historyInfo.text = if (cachedSamples.isEmpty()) {
            "Nicio măsurătoare salvată încă. Prima apare imediat ce aplicația rulează."
        } else {
            "${cachedSamples.size} măsurători în ultimele 30 de zile, ultima la ${fmt.format(Date(cachedSamples.last().ts))}."
        }

        val g = "Statistici"
        if (inRange.isEmpty()) {
            setRow(g, "Nivel minim", "—")
            setRow(g, "Nivel maxim", "—")
            setRow(g, "Temperatură maximă", "—")
            setRow(g, "Consum mediu", "—")
        } else {
            setRow(g, "Nivel minim", "${inRange.minOf { it.level }} %")
            setRow(g, "Nivel maxim", "${inRange.maxOf { it.level }} %")
            setRow(g, "Temperatură maximă", String.format(ro, "%.1f °C", inRange.maxOf { it.tempC }))
            val rate = Estimator.avgDischargeRate(inRange)
            setRow(
                g, "Consum mediu",
                if (rate == null) "se adună date" else String.format(ro, "%.1f %%/oră (≈ %s autonomie)", rate, Format.duration(100.0 / rate))
            )
        }
    }

    // ---------------------------------------------------------------- acțiuni

    private fun saveSettings() {
        val v = designInput.text.toString().trim().toIntOrNull()
        if (v == null || v < 1000 || v > 10000) {
            Toast.makeText(this, "Capacitatea trebuie să fie între 1000 și 10000 mAh", Toast.LENGTH_SHORT).show()
            return
        }
        val p = purchaseInput.text.toString().trim()
        if (p.isNotEmpty() && parsePurchase(p) == null) {
            Toast.makeText(this, "Data achiziției trebuie să arate ca 07.2023", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putInt("design_mah", v).putString("purchase", p).apply()
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

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Nu pot deschide această setare pe telefon", Toast.LENGTH_SHORT).show()
        }
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
            1 -> "încărcător"
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
