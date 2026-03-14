package com.example.smartledger.activity

import com.example.smartledger.util.AiHelper
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.smartledger.MainActivity
import com.example.smartledger.R
import com.example.smartledger.adapter.VerticalYearRenderer
import com.example.smartledger.data.AppDatabase
import com.example.smartledger.data.Electricity
import com.example.smartledger.data.Expense
import com.example.smartledger.data.MilkRecord
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.model.GradientColor
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class AnalyticsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var gestureDetector: GestureDetector
    private val db by lazy { AppDatabase.getDatabase(this) }

    // Charts - Main
    private lateinit var pieChart: PieChart

    // Charts - Electricity
    private lateinit var barChartUnits: BarChart
    private lateinit var barChartCost: BarChart
    private lateinit var barChartYoY: BarChart

    // Charts - Milk
    private lateinit var barChartMilkLitres: BarChart
    private lateinit var barChartMilkCost: BarChart
    private lateinit var barChartMilkYoY: BarChart

    // Charts - Expenses
    private lateinit var barChartExpenseMonthly: BarChart
    private lateinit var pieChartExpenseCategory: PieChart

    // UI Containers - Electricity
    private lateinit var layoutYoY: LinearLayout
    private lateinit var tvElecTitle: TextView
    private lateinit var btnElecPrev: ImageButton
    private lateinit var btnElecNext: ImageButton
    private lateinit var tvPrevYearLabel: TextView
    private lateinit var tvCurrYearLabel: TextView

    // UI Containers - Milk
    private lateinit var layoutMilkYoY: LinearLayout
    private lateinit var tvMilkTitle: TextView
    private lateinit var btnMilkPrev: ImageButton
    private lateinit var btnMilkNext: ImageButton
    private lateinit var tvMilkPrevYearLabel: TextView
    private lateinit var tvMilkCurrYearLabel: TextView

    // UI Containers - Expenses
    private lateinit var tvExpenseTitle: TextView
    private lateinit var btnExpensePrev: ImageButton
    private lateinit var btnExpenseNext: ImageButton
    private lateinit var layoutPieContainer: LinearLayout
    private lateinit var layoutExpenseLegend: LinearLayout

    // Indices for Carousels
    private var currentElecChartIndex = 0
    private var currentMilkChartIndex = 0
    private var currentExpenseChartIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        // Restore state if coming from config change
        savedInstanceState?.let {
            currentElecChartIndex = it.getInt("elecIndex", 0)
            currentMilkChartIndex = it.getInt("milkIndex", 0)
            currentExpenseChartIndex = it.getInt("expenseIndex", 0)
        }

        setupWindowInsets()
        setupNavigation()
        setupGestures()
        initializeViews()
        setupChartNavigation()
        setupMilkChartNavigation()
        setupExpenseNavigation()
        fixChartScrollConflict()
        observeCustomLedgers()
        loadData()

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("elecIndex", currentElecChartIndex)
        outState.putInt("milkIndex", currentMilkChartIndex)
        outState.putInt("expenseIndex", currentExpenseChartIndex)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        theme.applyStyle(R.style.Theme_SmartLedger, true)

        val bgColor = getThemeColor(android.R.attr.colorBackground)
        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurfaceContainer)
        val onSurfaceColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val primaryColor = getThemeColor(androidx.appcompat.R.attr.colorPrimary)

        findViewById<View>(R.id.main_content).setBackgroundColor(bgColor)

        val cardIds = listOf(R.id.cardSpending, R.id.cardElectricity, R.id.cardMilk, R.id.cardExpenses)
        cardIds.forEach { id ->
            findViewById<com.google.android.material.card.MaterialCardView>(id)?.setCardBackgroundColor(surfaceColor)
        }

        val yoyLabelIds = listOf(
            R.id.tvPrevYearLabel, R.id.tvCurrYearLabel,
            R.id.tvMilkPrevYearLabel, R.id.tvMilkCurrYearLabel
        )
        yoyLabelIds.forEach { id ->
            findViewById<TextView>(id)?.setTextColor(onSurfaceColor)
        }

        val allCharts = listOf(pieChart, barChartUnits, barChartCost, barChartYoY,
            barChartMilkLitres, barChartMilkCost, barChartMilkYoY,
            barChartExpenseMonthly, pieChartExpenseCategory)

        allCharts.forEach { chart ->
            chart.legend.textColor = onSurfaceColor

            if (chart is com.github.mikephil.charting.charts.BarLineChartBase<*>) {
                chart.xAxis.textColor = onSurfaceColor
                chart.axisLeft.textColor = onSurfaceColor
                chart.axisRight.textColor = onSurfaceColor
            }

            chart.data?.dataSets?.forEach { dataSet ->
                dataSet.valueTextColor = onSurfaceColor
            }

            if (chart is PieChart) {
                chart.setEntryLabelColor(onSurfaceColor)
                updatePieCenterText(chart, onSurfaceColor, primaryColor)
            }

            chart.invalidate()
        }

        updateExpenseLegendColors(onSurfaceColor, primaryColor)
    }

    private fun updatePieCenterText(chart: PieChart, onSurfaceColor: Int, primaryColor: Int) {
        val currentText = chart.centerText?.toString() ?: return
        if (currentText.contains("\n")) {
            val parts = currentText.split("\n")
            val spannable = SpannableString(currentText)
            spannable.setSpan(ForegroundColorSpan(Color.GRAY), 0, parts[0].length, 0)
            spannable.setSpan(RelativeSizeSpan(0.8f), 0, parts[0].length, 0)
            val startOfAmount = parts[0].length + 1
            spannable.setSpan(ForegroundColorSpan(primaryColor), startOfAmount, currentText.length, 0)
            spannable.setSpan(StyleSpan(Typeface.BOLD), startOfAmount, currentText.length, 0)
            spannable.setSpan(RelativeSizeSpan(1.5f), startOfAmount, currentText.length, 0)

            chart.centerText = spannable
        }
    }

    private fun updateExpenseLegendColors(textColor: Int, amountColor: Int) {
        for (i in 0 until layoutExpenseLegend.childCount) {
            val row = layoutExpenseLegend.getChildAt(i) as? LinearLayout
            row?.let {
                val tvCategoryName = it.getChildAt(1) as? TextView
                val tvAmountValue = it.getChildAt(2) as? TextView

                tvCategoryName?.setTextColor(textColor)
                tvAmountValue?.setTextColor(amountColor)
            }
        }
    }
    private fun initializeViews() {
        pieChart = findViewById(R.id.pieChart)

        // Electricity Views
        barChartUnits = findViewById(R.id.barChartUnits)
        barChartCost = findViewById(R.id.barChartCost)
        barChartYoY = findViewById(R.id.barChartYoY)
        layoutYoY = findViewById(R.id.layoutElectricityYoY)
        tvElecTitle = findViewById(R.id.tvElecTitle)
        btnElecPrev = findViewById(R.id.btnElecPrev)
        btnElecNext = findViewById(R.id.btnElecNext)
        tvPrevYearLabel = findViewById(R.id.tvPrevYearLabel)
        tvCurrYearLabel = findViewById(R.id.tvCurrYearLabel)

        // Milk Views
        barChartMilkLitres = findViewById(R.id.barChartMilkLitres)
        barChartMilkCost = findViewById(R.id.barChartMilkCost)
        barChartMilkYoY = findViewById(R.id.barChartMilkYoY)
        layoutMilkYoY = findViewById(R.id.layoutMilkYoY)
        tvMilkTitle = findViewById(R.id.tvMilkTitle)
        btnMilkPrev = findViewById(R.id.btnMilkPrev)
        btnMilkNext = findViewById(R.id.btnMilkNext)
        tvMilkPrevYearLabel = findViewById(R.id.tvMilkPrevYearLabel)
        tvMilkCurrYearLabel = findViewById(R.id.tvMilkCurrYearLabel)

        // Expense Views
        barChartExpenseMonthly = findViewById(R.id.barChartExpenseMonthly)
        pieChartExpenseCategory = findViewById(R.id.pieChartExpenseCategory)
        layoutPieContainer = findViewById(R.id.layoutPieContainer)
        layoutExpenseLegend = findViewById(R.id.layoutExpenseLegend)
        tvExpenseTitle = findViewById(R.id.tvExpenseTitle)
        btnExpensePrev = findViewById(R.id.btnExpensePrev)
        btnExpenseNext = findViewById(R.id.btnExpenseNext)
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun applyChartTheme(chart: com.github.mikephil.charting.charts.Chart<*>) {
        val textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)

        // Apply to legend
        chart.legend.textColor = textColor

        // Apply to coordinate-based charts
        if (chart is com.github.mikephil.charting.charts.BarLineChartBase<*>) {
            chart.xAxis.textColor = textColor
            chart.axisLeft.textColor = textColor
            chart.axisRight.textColor = textColor
        }

        // Apply to pie charts
        if (chart is PieChart) {
            chart.setCenterTextColor(textColor)
            chart.setHoleColor(Color.TRANSPARENT)
            chart.setEntryLabelColor(textColor)
        }

        chart.invalidate()
    }

    // CAROUSEL LOGIC: ELECTRICITY
    private fun setupChartNavigation() {
        val updateChartVisibility = {
            barChartUnits.visibility = View.GONE
            barChartCost.visibility = View.GONE
            layoutYoY.visibility = View.GONE

            when (currentElecChartIndex) {
                0 -> {
                    barChartUnits.visibility = View.VISIBLE
                    tvElecTitle.text = "Electricity: Units"
                    barChartUnits.animateY(800)
                }
                1 -> {
                    barChartCost.visibility = View.VISIBLE
                    tvElecTitle.text = "Electricity: Cost"
                    barChartCost.animateY(800)
                }
                2 -> {
                    layoutYoY.visibility = View.VISIBLE
                    tvElecTitle.text = "Electricity: Comparison"
                    barChartYoY.animateY(800)
                }
            }
        }

        btnElecNext.setOnClickListener {
            currentElecChartIndex = (currentElecChartIndex + 1) % 3
            updateChartVisibility()
        }

        btnElecPrev.setOnClickListener {
            currentElecChartIndex = if (currentElecChartIndex - 1 < 0) 2 else currentElecChartIndex - 1
            updateChartVisibility()
        }
    }

    // CAROUSEL LOGIC: MILK
    private fun setupMilkChartNavigation() {
        val updateChartVisibility = {
            barChartMilkLitres.visibility = View.GONE
            barChartMilkCost.visibility = View.GONE
            layoutMilkYoY.visibility = View.GONE

            when (currentMilkChartIndex) {
                0 -> {
                    barChartMilkLitres.visibility = View.VISIBLE
                    tvMilkTitle.text = "Milk: Litres"
                    barChartMilkLitres.animateY(800)
                }
                1 -> {
                    barChartMilkCost.visibility = View.VISIBLE
                    tvMilkTitle.text = "Milk: Cost"
                    barChartMilkCost.animateY(800)
                }
                2 -> {
                    layoutMilkYoY.visibility = View.VISIBLE
                    tvMilkTitle.text = "Milk: Comparison"
                    barChartMilkYoY.animateY(800)
                }
            }
        }

        btnMilkNext.setOnClickListener {
            currentMilkChartIndex = (currentMilkChartIndex + 1) % 3
            updateChartVisibility()
        }

        btnMilkPrev.setOnClickListener {
            currentMilkChartIndex = if (currentMilkChartIndex - 1 < 0) 2 else currentMilkChartIndex - 1
            updateChartVisibility()
        }
    }

    // CAROUSEL LOGIC: EXPENSES
    private fun setupExpenseNavigation() {
        val updateVisibility = {
            barChartExpenseMonthly.visibility = View.GONE
            layoutPieContainer.visibility = View.GONE

            if (currentExpenseChartIndex == 0) {
                barChartExpenseMonthly.visibility = View.VISIBLE
                tvExpenseTitle.text = "Expenses: Monthly"
                barChartExpenseMonthly.animateY(800)
            } else {
                layoutPieContainer.visibility = View.VISIBLE
                tvExpenseTitle.text = "Expenses: By Category"
                pieChartExpenseCategory.animateY(800)
            }
        }

        val toggle = View.OnClickListener {
            currentExpenseChartIndex = if (currentExpenseChartIndex == 0) 1 else 0
            updateVisibility()
        }

        btnExpenseNext.setOnClickListener(toggle)
        btnExpensePrev.setOnClickListener(toggle)
    }

    // SCROLL FIXES
    private fun fixChartScrollConflict() {
        val onTouchListener = View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        listOf(
            pieChart, barChartUnits, barChartCost, barChartYoY,
            barChartMilkLitres, barChartMilkCost, barChartMilkYoY,
            barChartExpenseMonthly, pieChartExpenseCategory
        ).forEach { it.setOnTouchListener(onTouchListener) }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            if (isPointInsideView(ev.rawX, ev.rawY, pieChart)) return super.dispatchTouchEvent(ev)

            val activeElec = when (currentElecChartIndex) {
                0 -> barChartUnits
                1 -> barChartCost
                2 -> barChartYoY
                else -> null
            }
            if (activeElec != null && isPointInsideView(ev.rawX, ev.rawY, activeElec)) {
                return super.dispatchTouchEvent(ev)
            }

            val activeMilk = when (currentMilkChartIndex) {
                0 -> barChartMilkLitres
                1 -> barChartMilkCost
                2 -> barChartMilkYoY
                else -> null
            }
            if (activeMilk != null && isPointInsideView(ev.rawX, ev.rawY, activeMilk)) {
                return super.dispatchTouchEvent(ev)
            }

            val activeExpense = if (currentExpenseChartIndex == 0) barChartExpenseMonthly else pieChartExpenseCategory
            if (activeExpense.visibility == View.VISIBLE && isPointInsideView(ev.rawX, ev.rawY, activeExpense)) {
                return super.dispatchTouchEvent(ev)
            }

            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]
        return (x > viewX && x < (viewX + view.width) && y > viewY && y < (viewY + view.height))
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = false
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > 100 && abs(vX) > 100) {
                    if (diffX > 0 && !drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
                return false
            }
        })
    }

    // DATA LOADING
    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val expenses = db.expenseDao().getAllRaw()
            val milkRecords = db.milkDao().getAllRaw()
            val electricity = db.electricityDao().getAllRaw()

            val totalExpense = expenses.filter { !it.isDeleted }.sumOf { it.amount }
            val totalMilkCost = milkRecords.filter { !it.isDeleted }.sumOf { it.totalAmount }
            val totalElec = electricity.filter { !it.isDeleted }.sumOf { it.amount ?: 0.0 }
            val grandTotal = totalExpense + totalMilkCost + totalElec

            val calendar = Calendar.getInstance()
            val currentMonthIdx = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)

            val elecList = electricity.filter { !it.isDeleted }.sortedBy { it.endDate }
            val expenseList = expenses.filter { !it.isDeleted }.sortedBy { it.date }
            val milkList = milkRecords.filter { !it.isDeleted }
                .sortedWith(compareBy({ it.year }, { it.monthIndex }))
            val historicalMilkList = milkList.filter {
                !(it.monthIndex == currentMonthIdx && it.year == currentYear)
            }

            withContext(Dispatchers.Main) {
                setupPieChart(totalExpense.toFloat(), totalMilkCost.toFloat(), totalElec.toFloat(), grandTotal.toFloat())
                setupElectricityCharts(elecList)
                setupElectricityYoYChart(elecList)
                setupMilkCharts(milkList)
                setupMilkYoYChart(milkList)
                setupExpenseCharts(expenseList)

                // --- ELECTRICITY BUTTONS VISIBILITY ---
                val elecBtnAi = findViewById<ImageButton>(R.id.btnElecAi)
                val hasElecData = elecList.isNotEmpty()
                elecBtnAi.visibility = if (hasElecData) View.VISIBLE else View.GONE
                btnElecPrev.visibility = if (hasElecData) View.VISIBLE else View.GONE
                btnElecNext.visibility = if (hasElecData) View.VISIBLE else View.GONE

                if (hasElecData) {
                    elecBtnAi.setOnClickListener {
                        val summary = AiHelper.summarizeElectricity(elecList)
                        startAiInsight("Electricity", summary, elecList.size)
                    }
                }

                // --- MILK BUTTONS VISIBILITY ---
                val milkBtnAi = findViewById<ImageButton>(R.id.btnMilkAi)
                val hasMilkData = milkList.isNotEmpty()
                milkBtnAi.visibility = if (hasMilkData) View.VISIBLE else View.GONE
                btnMilkPrev.visibility = if (hasMilkData) View.VISIBLE else View.GONE
                btnMilkNext.visibility = if (hasMilkData) View.VISIBLE else View.GONE

                if (hasMilkData) {
                    milkBtnAi.setOnClickListener {
                        val summary = AiHelper.summarizeMilk(historicalMilkList)
                        val lastRecord = historicalMilkList.lastOrNull()
                        val lastMonthName = lastRecord?.monthName ?: ""
                        val lastYear = lastRecord?.year ?: 0
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.MONTH, lastRecord?.monthIndex ?: 0)
                        cal.set(Calendar.YEAR, lastYear)
                        cal.add(Calendar.MONTH, 1)
                        val predictMonthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                        val summaryWithContext = "$summary\n\n[Last completed month: $lastMonthName $lastYear. Predict for: $predictMonthName only.]"
                        startAiInsight("Milk", summaryWithContext, historicalMilkList.size)
                    }
                }

                // --- EXPENSE BUTTONS VISIBILITY ---
                val expenseBtnAi = findViewById<ImageButton>(R.id.btnExpenseAi)
                val hasExpenseData = expenseList.isNotEmpty()
                expenseBtnAi.visibility = if (hasExpenseData) View.VISIBLE else View.GONE
                btnExpensePrev.visibility = if (hasExpenseData) View.VISIBLE else View.GONE
                btnExpenseNext.visibility = if (hasExpenseData) View.VISIBLE else View.GONE

                if (hasExpenseData) {
                    expenseBtnAi.setOnClickListener {
                        val summary = AiHelper.summarizeExpenses(expenseList)
                        startAiInsight("General Expenses", summary, expenseList.size)
                    }
                }
            }
        }
    }

    // ================== MILK CHARTS ==================
    private fun setupMilkCharts(records: List<MilkRecord>) {
        if (records.isEmpty()) return

        val entriesLitres = ArrayList<BarEntry>()
        val entriesCost = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        var maxLitres = 0f
        var maxCost = 0f

        records.forEachIndexed { index, record ->
            val liters = record.totalLiters.toFloat()
            val cost = record.totalAmount.toFloat()
            entriesLitres.add(BarEntry(index.toFloat(), liters))
            entriesCost.add(BarEntry(index.toFloat(), cost))

            val shortYear = record.year % 100
            val label = "${record.monthName.take(3)} '$shortYear"
            labels.add(label)

            if (liters > maxLitres) maxLitres = liters
            if (cost > maxCost) maxCost = cost
        }

        configureBarChart(
            barChartMilkLitres, entriesLitres, labels, 0,
            maxLitres * 1.2f, "Litres", isDecimal = true,
            gradientColors = getTrafficLightGradients(entriesLitres)
        )

        configureBarChart(
            barChartMilkCost, entriesCost, labels, 0,
            maxCost * 1.2f, "Cost", isDecimal = false,
            gradientColors = getTrafficLightGradients(entriesCost)
        )
    }
    private fun setupMilkYoYChart(records: List<MilkRecord>) {
        if (records.isEmpty()) {
            barChartMilkYoY.setNoDataText("No chart data available")
            return
        }

        val dataMap = HashMap<Int, Float>()
        records.forEach {
            val key = it.year * 100 + it.monthIndex
            val current = dataMap[key] ?: 0f
            dataMap[key] = current + it.totalLiters.toFloat()
        }

        val latestRecord = records.last()
        val latestYear = latestRecord.year
        val latestMonthIndex = latestRecord.monthIndex
        val entriesPrev = ArrayList<BarEntry>()
        val entriesCurr = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        val calendar = Calendar.getInstance()

        for (i in -11..0) {
            var targetMonth = latestMonthIndex + i
            var targetYear = latestYear

            while (targetMonth < 0) {
                targetMonth += 12
                targetYear -= 1
            }

            val currKey = targetYear * 100 + targetMonth
            val prevKey = (targetYear - 1) * 100 + targetMonth
            val valCurr = dataMap[currKey] ?: 0f
            val valPrev = dataMap[prevKey] ?: 0f
            val xIndex = (i + 11).toFloat()

            entriesCurr.add(BarEntry(xIndex, valCurr, targetYear.toString()))
            entriesPrev.add(BarEntry(xIndex, valPrev, (targetYear - 1).toString()))

            calendar.set(Calendar.MONTH, targetMonth)
            val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)
            labels.add(monthName)
        }

        tvMilkPrevYearLabel.text = " Previous Period"
        tvMilkCurrYearLabel.text = " Last 12 Months"

        val setPrev = BarDataSet(entriesPrev, "Previous")
        setPrev.color = Color.parseColor("#BBDEFB")
        setPrev.valueTextSize = 9f
        setPrev.valueTextColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        setPrev.setDrawValues(true)
        val setCurr = BarDataSet(entriesCurr, "Current")
        setCurr.color = Color.parseColor("#1976D2")
        setCurr.valueTextSize = 9f
        setCurr.valueTextColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        setCurr.setDrawValues(true)
        val decimalFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) String.format(Locale.getDefault(), "%.1f", value) else ""
            }
        }

        setPrev.valueFormatter = decimalFormatter
        setCurr.valueFormatter = decimalFormatter

        val barData = BarData(setPrev, setCurr)
        val groupSpace = 0.4f
        val barSpace = 0.0f
        val barWidth = 0.3f
        barData.barWidth = barWidth

        barChartMilkYoY.renderer = VerticalYearRenderer(barChartMilkYoY, barChartMilkYoY.animator, barChartMilkYoY.viewPortHandler)
        barChartMilkYoY.data = barData

        val xAxis = barChartMilkYoY.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setCenterAxisLabels(true)
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = 12f

        barChartMilkYoY.axisRight.isEnabled = false
        barChartMilkYoY.axisLeft.textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        barChartMilkYoY.axisLeft.axisMinimum = 0f
        barChartMilkYoY.description.isEnabled = false
        barChartMilkYoY.legend.isEnabled = false

        barChartMilkYoY.groupBars(0f, groupSpace, barSpace)
        barChartMilkYoY.setVisibleXRangeMaximum(6f)
        barChartMilkYoY.moveViewToX(12f)
        barChartMilkYoY.invalidate()
        barChartMilkYoY.animateY(1000)
    }

    // ================== ELECTRICITY CHARTS ==================
    private fun setupElectricityYoYChart(records: List<Electricity>) {
        if (records.isEmpty()) {
            barChartYoY.setNoDataText("No chart data available")
            return
        }

        val dataMap = HashMap<Int, Float>()
        val calendar = Calendar.getInstance()
        records.forEach {
            calendar.timeInMillis = it.endDate
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH)
            val key = y * 100 + m
            val current = dataMap[key] ?: 0f
            dataMap[key] = current + (it.totalUnits ?: 0.0).toFloat()
        }

        val latestRecordTime = records.maxOf { it.endDate }
        val entriesPrev = ArrayList<BarEntry>()
        val entriesCurr = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        val sdfMonth = SimpleDateFormat("MMM", Locale.getDefault())

        for (i in -11..0) {
            calendar.timeInMillis = latestRecordTime
            calendar.add(Calendar.MONTH, i)
            val currYear = calendar.get(Calendar.YEAR)
            val currMonth = calendar.get(Calendar.MONTH)
            val currKey = currYear * 100 + currMonth
            val prevKey = (currYear - 1) * 100 + currMonth
            val valCurr = dataMap[currKey] ?: 0f
            val valPrev = dataMap[prevKey] ?: 0f
            val xIndex = (i + 11).toFloat()

            entriesCurr.add(BarEntry(xIndex, valCurr, currYear.toString()))
            entriesPrev.add(BarEntry(xIndex, valPrev, (currYear - 1).toString()))
            labels.add(sdfMonth.format(calendar.time))
        }

        tvPrevYearLabel.text = " Previous Period"
        tvCurrYearLabel.text = " Last 12 Months"

        val setPrev = BarDataSet(entriesPrev, "Previous")
        setPrev.color = Color.parseColor("#D1C4E9")
        setPrev.valueTextSize = 9f
        setPrev.valueTextColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        setPrev.setDrawValues(true)

        val setCurr = BarDataSet(entriesCurr, "Current")
        setCurr.color = Color.parseColor("#5E35B1")
        setCurr.valueTextSize = 9f
        setCurr.valueTextColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        setCurr.setDrawValues(true)

        val zeroFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) String.format("%.0f", value) else ""
            }
        }

        setPrev.valueFormatter = zeroFormatter
        setCurr.valueFormatter = zeroFormatter

        val barData = BarData(setPrev, setCurr)
        val groupSpace = 0.4f
        val barSpace = 0.0f
        val barWidth = 0.3f
        barData.barWidth = barWidth

        barChartYoY.renderer = VerticalYearRenderer(barChartYoY, barChartYoY.animator, barChartYoY.viewPortHandler)
        barChartYoY.data = barData

        val xAxis = barChartYoY.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setCenterAxisLabels(true)
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = 12f

        barChartYoY.axisRight.isEnabled = false
        barChartYoY.axisLeft.textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        barChartYoY.axisLeft.axisMinimum = 0f
        barChartYoY.description.isEnabled = false
        barChartYoY.legend.isEnabled = false

        barChartYoY.groupBars(0f, groupSpace, barSpace)
        barChartYoY.setVisibleXRangeMaximum(6f)
        barChartYoY.moveViewToX(12f)
        barChartYoY.invalidate()
        barChartYoY.animateY(1000)
    }

    private fun setupElectricityCharts(records: List<Electricity>) {
        if (records.isEmpty()) return

        val entriesUnits = ArrayList<BarEntry>()
        val entriesCost = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        val sdf = SimpleDateFormat("MMM ''yy", Locale.getDefault())
        var maxUnits = 0f
        var maxCost = 0f

        records.forEachIndexed { index, record ->
            val units = (record.totalUnits ?: 0.0).toFloat()
            val cost = (record.amount ?: 0.0).toFloat()
            entriesUnits.add(BarEntry(index.toFloat(), units))
            entriesCost.add(BarEntry(index.toFloat(), cost))
            labels.add(sdf.format(Date(record.endDate)))
            if (units > maxUnits) maxUnits = units
            if (cost > maxCost) maxCost = cost
        }

        configureBarChart(
            barChartUnits, entriesUnits, labels, 0,
            maxUnits * 1.2f, "Units",
            gradientColors = getTrafficLightGradients(entriesUnits)
        )

        configureBarChart(
            barChartCost, entriesCost, labels, 0,
            maxCost * 1.2f, "Cost",
            gradientColors = getTrafficLightGradients(entriesCost)
        )
    }

    // ================== EXPENSE CHARTS ==================
    private fun setupExpenseCharts(records: List<Expense>) {
        if (records.isEmpty()) {
            barChartExpenseMonthly.setNoDataText("No chart data available")
            pieChartExpenseCategory.setNoDataText("No chart data available")
            return
        }

        // Monthly Bar Chart
        val monthlyMap = LinkedHashMap<String, Float>()
        val sdfMonth = SimpleDateFormat("MMM ''yy", Locale.getDefault())
        records.forEach {
            val key = sdfMonth.format(Date(it.date))
            monthlyMap[key] = (monthlyMap[key] ?: 0f) + it.amount.toFloat()
        }

        val entriesMonth = ArrayList<BarEntry>()
        val labelsMonth = ArrayList<String>()
        var index = 0f
        monthlyMap.forEach { (month, amount) ->
            entriesMonth.add(BarEntry(index, amount))
            labelsMonth.add(month)
            index++
        }

        configureBarChart(
            barChartExpenseMonthly, entriesMonth, labelsMonth, 0, 0f,
            "Monthly", isDecimal = false,
            gradientColors = getTrafficLightGradients(entriesMonth)
        )

        // Category Pie Chart
        val categoryMap = HashMap<String, Float>()
        var totalAmount = 0f
        records.forEach {
            val cat = it.title.trim().lowercase().replaceFirstChar { char -> char.uppercase() }
            val current = categoryMap[cat] ?: 0f
            categoryMap[cat] = current + it.amount.toFloat()
            totalAmount += it.amount.toFloat()
        }

        val pieEntries = ArrayList<PieEntry>()
        categoryMap.forEach { (cat, amount) ->
            pieEntries.add(PieEntry(amount, cat))
        }
        pieEntries.sortByDescending { it.value }

        val dataSet = PieDataSet(pieEntries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.JOYFUL_COLORS.toList()
        dataSet.setDrawValues(false)

        pieChartExpenseCategory.setDrawEntryLabels(false)
        pieChartExpenseCategory.description.isEnabled = false
        pieChartExpenseCategory.legend.isEnabled = false
        pieChartExpenseCategory.holeRadius = 58f
        pieChartExpenseCategory.transparentCircleRadius = 63f

        val formattedTotal = formatCompactNumber(totalAmount)
        val label = "Total"
        val fullText = "$label\n$formattedTotal"
        val spannable = SpannableString(fullText)
        spannable.setSpan(RelativeSizeSpan(0.8f), 0, label.length, 0)
        spannable.setSpan(ForegroundColorSpan(Color.GRAY), 0, label.length, 0)
        val startAmount = label.length + 1
        spannable.setSpan(RelativeSizeSpan(1.4f), startAmount, fullText.length, 0)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startAmount, fullText.length, 0)
        spannable.setSpan(ForegroundColorSpan(getColor(R.color.teal_main)), startAmount, fullText.length, 0)
        pieChartExpenseCategory.centerText = spannable

        val pieData = PieData(dataSet)
        pieChartExpenseCategory.data = pieData
        applyChartTheme(pieChartExpenseCategory)
        pieChartExpenseCategory.animateY(1400)
        pieChartExpenseCategory.invalidate()

        // Custom Legend
        layoutExpenseLegend.removeAllViews()
        pieEntries.forEachIndexed { i, entry ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding(0, 8, 0, 8)

            val colorBox = View(this)
            val color = dataSet.colors[i % dataSet.colors.size]
            val params = LinearLayout.LayoutParams(40, 40)
            params.marginEnd = 24
            colorBox.layoutParams = params
            colorBox.setBackgroundColor(color)

            val percentage = (entry.value / totalAmount) * 100
            val tvName = TextView(this)
            tvName.text = "${entry.label} (${String.format("%.0f%%", percentage)})"
            tvName.textSize = 14f
            tvName.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface))
            tvName.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val tvAmount = TextView(this)
            tvAmount.text = String.format(Locale.getDefault(), "%.0f", entry.value)
            tvAmount.textSize = 14f
            tvAmount.setTypeface(null, Typeface.BOLD)
            tvAmount.setTextColor(getColor(R.color.teal_main))

            row.addView(colorBox)
            row.addView(tvName)
            row.addView(tvAmount)
            layoutExpenseLegend.addView(row)
        }
    }

    private fun configureBarChart(
        chart: BarChart,
        entries: List<BarEntry>,
        labels: List<String>,
        singleColor: Int,
        yAxisMax: Float,
        label: String,
        isDecimal: Boolean = false,
        gradientColors: List<GradientColor>? = null
    ) {
        val dataSet = BarDataSet(entries, label)

        if (gradientColors != null) {
            dataSet.gradientColors = gradientColors
        } else {
            dataSet.color = singleColor
        }

        dataSet.valueTextColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        dataSet.valueTextSize = 12f
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (isDecimal) String.format(Locale.getDefault(), "%.1f", value)
                else String.format(Locale.getDefault(), "%.0f", value)
            }
        }

        val data = BarData(dataSet)
        data.barWidth = 0.6f
        chart.data = data

        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.granularity = 1f

        chart.axisRight.isEnabled = false
        chart.axisLeft.textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        chart.axisLeft.axisMinimum = 0f
        if (yAxisMax > 0) chart.axisLeft.axisMaximum = yAxisMax
        else chart.axisLeft.resetAxisMaximum()

        chart.setVisibleXRangeMaximum(6f)
        chart.moveViewToX(entries.size.toFloat())
        chart.extraBottomOffset = 10f
        chart.invalidate()
        chart.animateY(1400)
    }

    private fun setupPieChart(exp: Float, milk: Float, elec: Float, grandTotal: Float) {
        if (grandTotal <= 0) {
            pieChart.clear()
            pieChart.invalidate()
            return
        }

        val entries = ArrayList<PieEntry>()
        if (exp > 0) entries.add(PieEntry(exp, "Expenses"))
        if (milk > 0) entries.add(PieEntry(milk, "Milk"))
        if (elec > 0) entries.add(PieEntry(elec, "Electricity"))

        val dataSet = PieDataSet(entries, "")
        val colors = ArrayList<Int>()
        colors.add(Color.parseColor("#179A9D"))
        colors.add(Color.parseColor("#006B6E"))
        colors.add(Color.parseColor("#B2E0E1"))
        dataSet.colors = colors
        dataSet.yValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.WHITE

        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.description.isEnabled = false

        val formattedTotal = formatCompactNumber(grandTotal)
        val label = "Total Spent"
        val fullText = "$label\n$formattedTotal"
        val spannable = SpannableString(fullText)
        spannable.setSpan(RelativeSizeSpan(0.8f), 0, label.length, 0)
        spannable.setSpan(ForegroundColorSpan(Color.GRAY), 0, label.length, 0)
        val startAmount = label.length + 1
        spannable.setSpan(RelativeSizeSpan(1.6f), startAmount, fullText.length, 0)
        spannable.setSpan(StyleSpan(Typeface.BOLD), startAmount, fullText.length, 0)
        spannable.setSpan(ForegroundColorSpan(getColor(R.color.teal_main)), startAmount, fullText.length, 0)
        pieChart.centerText = spannable

        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.holeRadius = 58f
        pieChart.transparentCircleRadius = 63f
        pieChart.legend.isEnabled = true
        pieChart.legend.textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        pieChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        pieChart.animateY(1400)
        pieChart.invalidate()
    }

    private fun formatCompactNumber(value: Float): String {
        if (value >= 1_000_000_000) return String.format(Locale.getDefault(), "%.1fB", value / 1_000_000_000)
        if (value >= 1_000_000) return String.format(Locale.getDefault(), "%.1fM", value / 1_000_000)
        if (value >= 1_000) return String.format(Locale.getDefault(), "%.1fk", value / 1_000)
        return String.format(Locale.getDefault(), "%.0f", value)
    }

    private fun startAiInsight(dataType: String, dataSummary: String, recordCount: Int) {
        val intent = Intent(this, AiInsightActivity::class.java).apply {
            putExtra("DATA_TYPE", dataType)
            putExtra("DATA_SUMMARY", dataSummary)
            putExtra("RECORD_COUNT", recordCount)
        }
        startActivity(intent)
    }


    private fun getTrafficLightGradients(entries: List<BarEntry>): List<GradientColor> {
        val max = entries.maxOfOrNull { it.y } ?: 1f
        return entries.map { entry ->
            when {
                entry.y >= max * 0.80f -> GradientColor(
                    Color.parseColor("#FF8A80"),
                    Color.parseColor("#B71C1C")
                )
                entry.y >= max * 0.40f -> GradientColor(
                    Color.parseColor("#FFD180"),
                    Color.parseColor("#E65100")
                )
                else -> GradientColor(
                    Color.parseColor("#A5D6A7"),
                    Color.parseColor("#1B5E20")
                )
            }
        }
    }

    private fun setupNavigation() {
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)

        topAppBar.setNavigationOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        navigationView.setNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        val id = item.itemId

        Handler(Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                val customLedgers = withContext(Dispatchers.IO) { db.customLedgerDao().getAllLedgersList() }
                val clickedLedger = customLedgers.find { (it.id + 1000) == id }

                if (clickedLedger != null) {
                    val intent = Intent(this@AnalyticsActivity, GenericLedgerActivity::class.java)
                    intent.putExtra("ledger_template", clickedLedger)
                    startActivity(intent)
                    finish()
                } else {
                    // Static Navigation
                    when (id) {
                        R.id.nav_dashboard -> {
                            val intent = Intent(this@AnalyticsActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        }
                        R.id.nav_analytics -> { /* Already here */ }
                        R.id.nav_electricity -> {
                            val intent = Intent(this@AnalyticsActivity, ElectricityActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                            finish()
                        }
                        R.id.nav_milk -> {
                            val intent = Intent(this@AnalyticsActivity, MilkActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                            finish()
                        }
                        R.id.nav_expenses -> {
                            val intent = Intent(this@AnalyticsActivity, ExpenseActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                            finish()
                        }
                        R.id.nav_trash -> {
                            startActivity(Intent(this@AnalyticsActivity, TrashBinActivity::class.java))
                            finish()
                        }
                        R.id.nav_calculator -> startActivity(Intent(this@AnalyticsActivity, CalculatorActivity::class.java))
                        R.id.nav_backup, R.id.nav_restore, R.id.nav_wipe_data -> {
                            val intent = Intent(this@AnalyticsActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                            Toast.makeText(this@AnalyticsActivity, "Manage these settings from Dashboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }, 250)
        return true
    }

    private fun setupWindowInsets() {
        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        setupHeader()
        findViewById<NavigationView>(R.id.navigationView).setCheckedItem(R.id.nav_analytics)
    }

    private fun setupHeader() {
        val navView = findViewById<NavigationView>(R.id.navigationView)
        val headerView = navView.getHeaderView(0)
        val tvName = headerView.findViewById<TextView>(R.id.headerName)
        val tvBackup = headerView.findViewById<TextView>(R.id.tvLastBackup)
        val prefs = getSharedPreferences("SmartLedgerPrefs", MODE_PRIVATE)

        tvName.text = prefs.getString("user_name", "Enter your name")
        tvBackup.text = "Last backup: ${prefs.getString("last_backup", "Never")}"

        if (this is MainActivity) {
            tvName.setOnClickListener {
                this.showEditNameDialog(tvName)
            }
        } else {
            tvName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            tvName.setOnClickListener(null)
        }
    }
    private fun observeCustomLedgers() {
        lifecycleScope.launch {
            db.customLedgerDao().getAllLedgers().collect { ledgers ->
                val navigationView = findViewById<NavigationView>(R.id.navigationView)
                val menu = navigationView.menu
                val staticIds = setOf(R.id.nav_dashboard, R.id.nav_electricity, R.id.nav_milk, R.id.nav_expenses)
                val toRemove = mutableListOf<Int>()

                for (i in 0 until menu.size()) {
                    val item = menu.getItem(i)
                    if (item.groupId == R.id.group_main && !staticIds.contains(item.itemId)) {
                        toRemove.add(item.itemId)
                    }
                }
                toRemove.forEach { menu.removeItem(it) }

                ledgers.forEachIndexed { index, ledger ->
                    val iconResId = resources.getIdentifier(ledger.iconName, "drawable", packageName)

                    val menuItem = menu.add(
                        R.id.group_main,
                        ledger.id + 1000,
                        10 + index,
                        ledger.name
                    )

                    menuItem.setIcon(if (iconResId != 0) iconResId else R.drawable.ic_star)
                    menuItem.setCheckable(true)
                    menuItem.icon?.setTint(androidx.core.content.ContextCompat.getColor(this@AnalyticsActivity, R.color.teal_main))
                }
            }
        }
    }
}