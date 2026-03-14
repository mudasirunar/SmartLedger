package com.example.smartledger.adapter

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import kotlin.math.abs

class VerticalYearRenderer(
    chart: BarDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler
) : BarChartRenderer(chart, animator, viewPortHandler) {

    private val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = Utils.convertDpToPixel(10f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun drawValues(c: Canvas) {
        super.drawValues(c)

        val dataSets = mChart.barData.dataSets
        val buffer = mBarBuffers

        c.save()
        c.clipRect(mViewPortHandler.contentRect)

        for (i in dataSets.indices) {
            val dataSet = dataSets[i]
            if (!shouldDrawValues(dataSet) || dataSet.entryCount == 0) continue

            yearPaint.color = if (i == 0) Color.BLACK else Color.WHITE
            val barBuffer = buffer[i]
            val fontHeightOffset = ((yearPaint.descent() + yearPaint.ascent()) / 2f)

            for (j in 0 until dataSet.entryCount * 4 step 4) {
                val index = j / 4
                val entry = dataSet.getEntryForIndex(index)
                val yearText = entry.data as? String

                if (yearText != null) {
                    val left = barBuffer.buffer[j]
                    val right = barBuffer.buffer[j + 2]
                    val top = barBuffer.buffer[j + 1]
                    val bottom = barBuffer.buffer[j + 3]

                    val x = (left + right) / 2f
                    val y = (top + bottom) / 2f

                    if (!mViewPortHandler.isInBoundsRight(left)) break
                    if (!mViewPortHandler.isInBoundsLeft(right)) continue

                    if (abs(top - bottom) > 50) {
                        c.save()
                        c.rotate(-90f, x, y)
                        c.drawText(yearText, x, y - fontHeightOffset, yearPaint)
                        c.restore()
                    }
                }
            }
        }
        c.restore()
    }
}