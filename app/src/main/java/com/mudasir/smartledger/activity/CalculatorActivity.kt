package com.mudasir.smartledger.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mudasir.smartledger.adapter.HistoryAdapter
import com.mudasir.smartledger.R
import com.mudasir.smartledger.data.AppDatabase
import com.mudasir.smartledger.data.CalcHistory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import com.mudasir.smartledger.util.applySystemBarPadding
import java.text.DecimalFormat
import kotlin.text.iterator

class CalculatorActivity : AppCompatActivity() {

    private lateinit var tvInput: TextView
    private lateinit var tvResult: TextView
    private lateinit var inputScrollView: ScrollView
    private lateinit var keypadGrid: GridLayout
    private lateinit var rvHistory: RecyclerView
    private lateinit var btnHistoryToggle: ImageButton
    private lateinit var db: AppDatabase
    private lateinit var historyAdapter: HistoryAdapter
    private var lastNumeric: Boolean = false
    private var stateError: Boolean = false
    private var lastDot: Boolean = false
    private var isHistoryOpen: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_calculator)

        db = AppDatabase.getDatabase(this)

        setupWindowInsets()
        setupToolbar()
        setupCalculator()
        setupHistory()
    }

    private fun setupWindowInsets() {
        findViewById<View>(R.id.main).applySystemBarPadding()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupHistory() {
        rvHistory = findViewById(R.id.rvHistory)
        btnHistoryToggle = findViewById(R.id.btnHistoryToggle)
        keypadGrid = findViewById(R.id.keypadGrid)

        historyAdapter = HistoryAdapter { result ->
            tvInput.text = result
            tvResult.text = ""
            isHistoryOpen = false
            toggleHistoryUI()
        }
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        lifecycleScope.launch {
            db.calcDao().getAllHistory().collect { list ->
                historyAdapter.setData(list)
            }
        }

        btnHistoryToggle.setOnClickListener {
            playHapticFeedback()
            isHistoryOpen = !isHistoryOpen
            toggleHistoryUI()
        }
    }

    private fun toggleHistoryUI() {
        val tealColor = ContextCompat.getColor(this, R.color.teal_main)
        val greyColor = ContextCompat.getColor(this, R.color.text_secondary)

        btnHistoryToggle.imageTintList = ColorStateList.valueOf(if (isHistoryOpen) tealColor else greyColor)

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        if (isHistoryOpen) {
            rvHistory.visibility = View.VISIBLE
            keypadGrid.animate()
                .translationX(screenWidth)
                .setDuration(300)
                .withEndAction { keypadGrid.visibility = View.INVISIBLE }
                .start()
        } else {
            keypadGrid.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
            keypadGrid.animate()
                .translationX(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun setupCalculator() {
        tvInput = findViewById(R.id.tvInput)
        tvResult = findViewById(R.id.tvResult)
        tvResult.movementMethod = ScrollingMovementMethod()
        inputScrollView = findViewById(R.id.inputScrollView)
        tvInput.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom) {
                inputScrollView.post { inputScrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }

        val numberButtons = listOf(
            R.id.btn0, R.id.btn00, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        val listener = View.OnClickListener { v ->
            playHapticFeedback()
            if (stateError) {
                tvInput.text = (v as Button).text
                stateError = false
            } else {
                tvInput.append((v as Button).text)
            }
            lastNumeric = true
            calculateLiveResult()
        }

        numberButtons.forEach { id -> findViewById<Button>(id).setOnClickListener(listener) }

        findViewById<Button>(R.id.btnDot).setOnClickListener {
            playHapticFeedback()
            if (lastNumeric && !stateError && !lastDot) {
                tvInput.append(".")
                lastNumeric = false
                lastDot = true
            }
        }

        val operatorButtons = listOf(R.id.btnAdd, R.id.btnSubtract, R.id.btnMultiply, R.id.btnDivide)
        operatorButtons.forEach { id ->
            findViewById<Button>(id).setOnClickListener {
                playHapticFeedback()
                if (lastNumeric && !stateError) {
                    tvInput.append((it as Button).text)
                    lastNumeric = false
                    lastDot = false
                }
            }
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            playHapticFeedback()
            tvInput.text = ""
            tvResult.text = ""
            lastNumeric = false
            stateError = false
            lastDot = false
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            playHapticFeedback()
            val text = tvInput.text.toString()
            if (text.isNotEmpty()) {
                tvInput.text = text.dropLast(1)
                if (tvInput.text.isNotEmpty()) {
                    val lastChar = tvInput.text.last()
                    lastNumeric = lastChar.isDigit()
                    val lastPart = tvInput.text.split("+", "-", "*", "/").last()
                    lastDot = lastPart.contains(".")
                    calculateLiveResult()
                } else {
                    lastNumeric = false
                    lastDot = false
                    tvResult.text = ""
                }
            } else {
                tvResult.text = ""
            }
        }

        findViewById<Button>(R.id.btnEquals).setOnClickListener {
            playHapticFeedback()
            onEqual()
        }
    }

    private fun calculateLiveResult() {
        if (lastNumeric && !stateError) {
            var txt = tvInput.text.toString()
            txt = txt.replace("÷", "/")
                .replace("×", "*")
                .replace("−", "-")
            try {
                if (txt.contains("+") || txt.contains("-") || txt.contains("*") || txt.contains("/")) {
                    val result = evaluateMathString(txt)
                    val df = DecimalFormat("#.##########")
                    tvResult.text = df.format(result)
                } else {
                    tvResult.text = ""
                }
            } catch (e: Exception) {
                // Ignore errors while typing
            }
        }
    }

    private fun onEqual() {
        if (lastNumeric && !stateError) {
            val txt = tvInput.text.toString()
            val liveRes = tvResult.text.toString()

            if (liveRes.isEmpty()) return

            // Save to DB
            lifecycleScope.launch {
                db.calcDao().insert(CalcHistory(expression = txt, result = liveRes))
            }

            val scaleFactor = tvInput.textSize / tvResult.textSize
            tvResult.pivotX = tvResult.width.toFloat()
            tvResult.pivotY = tvResult.height.toFloat()
            val inputBottom = tvInput.y + tvInput.height
            val resultBottom = tvResult.y + tvResult.height
            val distanceY = -(resultBottom - inputBottom)
            tvInput.animate().alpha(0f).setDuration(300).start()
            tvResult.animate()
                .translationY(distanceY)
                .scaleX(scaleFactor)
                .scaleY(scaleFactor)
                .setDuration(400)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        tvInput.text = liveRes
                        tvInput.alpha = 1f
                        tvResult.text = ""
                        tvResult.translationY = 0f
                        tvResult.scaleX = 1f
                        tvResult.scaleY = 1f
                        tvResult.pivotX = tvResult.width / 2f
                        tvResult.pivotY = tvResult.height / 2f
                        lastDot = liveRes.contains(".")
                    }
                })
                .start()
        }
    }

    private fun evaluateMathString(expression: String): Double {
        val tokens = ArrayList<String>()
        var currentNumber = StringBuilder()
        for (char in expression) {
            if (char.isDigit() || char == '.') {
                currentNumber.append(char)
            } else {
                if (currentNumber.isNotEmpty()) {
                    tokens.add(currentNumber.toString())
                    currentNumber.clear()
                }
                tokens.add(char.toString())
            }
        }
        if (currentNumber.isNotEmpty()) tokens.add(currentNumber.toString())
        var i = 0
        while (i < tokens.size) {
            if (tokens[i] == "*" || tokens[i] == "/") {
                val operator = tokens[i]
                val leftVal = tokens[i - 1].toDouble()
                val rightVal = tokens[i + 1].toDouble()
                var result = 0.0
                if (operator == "*") result = leftVal * rightVal
                else if (operator == "/") {
                    if (rightVal == 0.0) throw ArithmeticException("Div by 0")
                    result = leftVal / rightVal
                }
                tokens[i - 1] = result.toString()
                tokens.removeAt(i)
                tokens.removeAt(i)
                i--
            }
            i++
        }
        var finalResult = tokens[0].toDouble()
        var j = 1
        while (j < tokens.size) {
            val operator = tokens[j]
            val nextVal = tokens[j + 1].toDouble()
            if (operator == "+") finalResult += nextVal
            else if (operator == "-") finalResult -= nextVal
            j += 2
        }
        return finalResult
    }

    private fun playHapticFeedback() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
        } else {
            // Backward compatibility for older devices (10ms burst)
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

}