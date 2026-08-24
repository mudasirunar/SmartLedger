package com.mudasir.smartledger.activity

import com.mudasir.smartledger.util.AiHelper
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.mudasir.smartledger.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

class AiInsightActivity : AppCompatActivity() {

    private lateinit var tvContent: TextView
    private lateinit var btnPredict: com.google.android.material.button.MaterialButton
    private lateinit var loadingLayout: LinearLayout
    private lateinit var loaderContainer: LinearLayout
    private lateinit var tvWaking: TextView
    private lateinit var btnRetry: com.google.android.material.button.MaterialButton
    private lateinit var btnRetryPrediction: com.google.android.material.button.MaterialButton
    private lateinit var progressIndicator: LinearProgressIndicator
    private var logoAnimator: ValueAnimator? = null
    private var colorWaveAnimator: ValueAnimator? = null
    private var predictionErrorIndex: Int = -1
    private lateinit var ivErrorIcon: ImageView
    private lateinit var ivLogo: ImageView
    private var dataType = ""
    private var dataSummary = ""
    private var recordCount = 0
    private var isErrorActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ai_insight)

        val rootLayout = findViewById<View>(R.id.aiRootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dataType = intent.getStringExtra("DATA_TYPE") ?: ""
        dataSummary = intent.getStringExtra("DATA_SUMMARY") ?: ""
        recordCount = intent.getIntExtra("RECORD_COUNT", 0)

        btnRetry = findViewById(R.id.btnRetry)
        btnRetry.setOnClickListener { startAiTask() }

        btnRetryPrediction = findViewById(R.id.btnRetryPrediction)
        btnRetryPrediction.setOnClickListener { runPrediction() }

        initViews()
        applyLedgerHeader()
        setupAnimations()
        startAiTask()
    }

    private fun initViews() {
        val toolbar = findViewById<MaterialToolbar>(R.id.aiToolbar)
        toolbar.title = "$dataType Analysis"
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvContent = findViewById(R.id.tvAiContent)
        btnPredict = findViewById(R.id.btnPredict)
        loadingLayout = findViewById(R.id.loadingLayout)
        loaderContainer = findViewById(R.id.loaderContainer)
        tvWaking = findViewById(R.id.tvWakingGemini)
        ivErrorIcon = findViewById(R.id.ivErrorIcon)
        ivLogo = findViewById(R.id.ivGeminiLogoBg)

        progressIndicator = LinearProgressIndicator(this).apply {
            isIndeterminate = true
            trackThickness = 12
            setIndicatorColor(Color.parseColor("#4285F4"), Color.parseColor("#9B72CB"), Color.parseColor("#179A9D"))
        }
        loaderContainer.addView(progressIndicator)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        theme.applyStyle(R.style.Theme_SmartLedger, true)

        val onSurfaceColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val bgColor = getThemeColor(android.R.attr.colorBackground)

        findViewById<View>(R.id.aiRootLayout).setBackgroundColor(bgColor)
        findViewById<LinearLayout>(R.id.bottomContainer).setBackgroundColor(surfaceColor)

        val currentText = tvContent.text
        if (currentText is android.text.Spannable) {
            val spans = currentText.getSpans(0, currentText.length, android.text.style.ForegroundColorSpan::class.java)
            for (span in spans) {
                if (span.foregroundColor != Color.RED) {
                    val start = currentText.getSpanStart(span)
                    val end = currentText.getSpanEnd(span)
                    currentText.removeSpan(span)
                    currentText.setSpan(android.text.style.ForegroundColorSpan(onSurfaceColor), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        tvContent.setTextColor(onSurfaceColor)
        tvWaking.setTextColor(onSurfaceColor)
        findViewById<TextView>(R.id.tvAiDisclaimer).setTextColor(onSurfaceColor)

        val geminiBlue = resources.getColor(R.color.gemini_blue, null)
        btnRetry.setBackgroundColor(geminiBlue)
        btnRetryPrediction.setBackgroundColor(geminiBlue)

        applyLedgerHeader()
    }

    private fun startTypewriterEffect(text: String, isAppend: Boolean = false) {
        val onSurfaceColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)

        if (!isAppend) {
            tvContent.text = ""
            tvContent.setTextColor(onSurfaceColor)
            tvContent.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            val startPos = tvContent.text.length
            val delayTime = if (text.length > 500) 1L else if (isAppend) 2L else 5L
            text.forEachIndexed { index, char ->
                tvContent.append(char.toString())
                if (isAppend && (index % 20 == 0 || index == text.length - 1)) {
                    val spannable = tvContent.text as android.text.Spannable
                    spannable.setSpan(
                        android.text.style.ForegroundColorSpan(onSurfaceColor),
                        startPos, tvContent.text.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                if (index % 12 == 0) playSubtleTick()

                kotlinx.coroutines.delay(delayTime)
                if (index % 5 == 0) {
                    findViewById<NestedScrollView>(R.id.scrollContent).fullScroll(View.FOCUS_DOWN)
                }
            }
            findViewById<NestedScrollView>(R.id.scrollContent).fullScroll(View.FOCUS_DOWN)
            playCompletionHaptic()

            if (!isAppend) updatePredictButtonState()
        }
    }
    private fun playSubtleTick() {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrator.vibrate(2)
        }
    }

    private fun playCompletionHaptic() {
        val vibrator = getVibrator()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            vibrator.vibrate(40)
        }
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun startAiTask() {
        isErrorActive = false
        loadingLayout.visibility = View.VISIBLE
        btnRetry.visibility = View.GONE
        tvContent.visibility = View.GONE
        progressIndicator.visibility = View.VISIBLE

        findViewById<LinearLayout>(R.id.bottomContainer).visibility = View.GONE

        startLogoAnimation()

        lifecycleScope.launch {
            val result = AiHelper.getInsight(dataType, dataSummary)
            progressIndicator.visibility = View.GONE

            if (AiHelper.isError(result)) {
                isErrorActive = true
                stopLogoAnimation()
                tvWaking.text = result
                tvWaking.setTextColor(Color.RED)
                ivErrorIcon.visibility = View.VISIBLE
                btnRetry.visibility = View.VISIBLE
            } else {
                isErrorActive = false
                stopLogoAnimation()
                loadingLayout.visibility = View.GONE
                findViewById<LinearLayout>(R.id.bottomContainer).visibility = View.VISIBLE
                startTypewriterEffect(AiHelper.formatAiResponse(result))
            }
        }
    }

    private fun runPrediction() {
        progressIndicator.visibility = View.VISIBLE
        btnPredict.visibility = View.GONE
        btnRetryPrediction.visibility = View.GONE

        if (predictionErrorIndex != -1) {
            val currentText = tvContent.text.toString()
            tvContent.text = currentText.substring(0, predictionErrorIndex)
            predictionErrorIndex = -1
        }

        startLogoAnimation()

        lifecycleScope.launch {
            val pred = AiHelper.getPrediction(dataType, dataSummary)
            progressIndicator.visibility = View.GONE
            stopLogoAnimation()

            if (AiHelper.isError(pred)) {
                // We set isErrorActive to false for predictions so theme change doesn't turn everything RED
                predictionErrorIndex = tvContent.text.length
                val errorSpan = android.text.SpannableString("\n\n❌ Prediction Failed: $pred")
                errorSpan.setSpan(android.text.style.ForegroundColorSpan(Color.RED), 0, errorSpan.length, 0)
                tvContent.append(errorSpan)

                btnRetryPrediction.visibility = View.VISIBLE
                btnRetryPrediction.setBackgroundColor(resources.getColor(R.color.gemini_blue, null))
            } else {
                val predictionHeader = "\n\n━━━━━━━━━━━━━━\n🔮 AI PREDICTION\n"
                tvContent.append(predictionHeader)
                // Use isAppend = true to preserve theme colors
                startTypewriterEffect(AiHelper.formatAiResponse(pred), isAppend = true)
                btnRetryPrediction.visibility = View.GONE
            }
        }
    }

    private fun applyLedgerHeader() {
        val toolbar = findViewById<MaterialToolbar>(R.id.aiToolbar)
        val colors = intArrayOf(
            Color.parseColor("#1A237E"),
            Color.parseColor("#006064"),
            Color.parseColor("#1A237E")
        )
        val gradientDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors)
        toolbar.background = gradientDrawable
        window.statusBarColor = Color.parseColor("#1A237E")
    }




    private fun startLogoAnimation() {
        stopLogoAnimation()
        logoAnimator = ValueAnimator.ofFloat(0.35f, 0.65f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { ivLogo.alpha = it.animatedValue as Float }
            start()
        }

        colorWaveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val color1 = Color.parseColor("#4285F4")
                val color2 = Color.parseColor("#9B72CB")
                val color3 = Color.parseColor("#179A9D")

                val currentColor = when {
                    fraction < 0.5f -> interpolateColor(color1, color2, fraction * 2)
                    else -> interpolateColor(color2, color3, (fraction - 0.5f) * 2)
                }
                ivLogo.setColorFilter(currentColor, PorterDuff.Mode.SRC_IN)
            }
            start()
        }
    }


    private fun stopLogoAnimation() {
        logoAnimator?.cancel()
        colorWaveAnimator?.cancel()
        ivLogo.alpha = 0.30f
        ivLogo.clearColorFilter()
    }

    private fun interpolateColor(a: Int, b: Int, proportion: Float): Int {
        val hsva = FloatArray(3)
        val hsvb = FloatArray(3)
        Color.colorToHSV(a, hsva)
        Color.colorToHSV(b, hsvb)
        for (i in 0..2) {
            hsvb[i] = hsva[i] + (hsvb[i] - hsva[i]) * proportion
        }
        return Color.HSVToColor(hsvb)
    }

    private fun updatePredictButtonState() {
        if (dataType == "Expense") {
            btnPredict.visibility = View.GONE
            return
        } else {
            btnPredict.visibility = View.VISIBLE
        }

        if (recordCount >= 6) {
            btnPredict.isEnabled = true
            btnPredict.alpha = 1.0f
            btnPredict.text = "Predict Next Month"

            btnPredict.setOnClickListener {
                runPrediction()
            }
        } else {
            btnPredict.isEnabled = false
            btnPredict.alpha = 0.5f
            btnPredict.text = "Predict (6+ Months Required)"
        }
    }

    private fun setupAnimations() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val pos = anim.animatedValue as Float
                val shader = LinearGradient(0f, 0f, tvWaking.width.toFloat(), 0f,
                    intArrayOf(Color.parseColor("#4285F4"), Color.parseColor("#9B72CB"), Color.parseColor("#179A9D"), Color.parseColor("#4285F4")),
                    floatArrayOf(0f, pos, pos + 0.1f, 1f), Shader.TileMode.REPEAT)
                tvWaking.paint.shader = shader; tvWaking.invalidate()
            }
        }.start()
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}