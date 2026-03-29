package com.example.peyarealnumbers

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Locale

class TrendChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<Float> = emptyList()
    private var dataLabels: List<String> = emptyList()
    
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B894")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintDots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B894")
        style = Paint.Style.FILL
    }

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B2BEC3")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    fun setData(points: List<Float>, labels: List<String> = emptyList()) {
        this.dataPoints = points
        this.dataLabels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.size < 2) return

        val width = width.toFloat()
        val height = height.toFloat()
        val paddingLeft = 100f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 80f
        
        val maxVal = dataPoints.maxOrNull() ?: 1f
        val minVal = dataPoints.minOrNull() ?: 0f
        val range = if (maxVal == minVal) 1f else maxVal - minVal

        // Dibujar líneas de guía (Ejes/Grid)
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = paddingTop + i * (height - paddingTop - paddingBottom) / gridLines
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, paintGrid)
        }

        // Dibujar etiquetas de valores (Min/Max) en el eje Y
        paintText.textAlign = Paint.Align.LEFT
        canvas.drawText(String.format(Locale.getDefault(), "$%.0f", maxVal), 10f, paddingTop + 10f, paintText)
        canvas.drawText(String.format(Locale.getDefault(), "$%.0f", minVal), 10f, height - paddingBottom, paintText)

        val path = Path()
        val fillPath = Path()
        
        val xStep = (width - paddingLeft - paddingRight) / (dataPoints.size - 1)
        
        paintText.textAlign = Paint.Align.CENTER
        for (i in dataPoints.indices) {
            val x = paddingLeft + i * xStep
            val normalizedY = (dataPoints[i] - minVal) / range
            val y = height - paddingBottom - (normalizedY * (height - paddingTop - paddingBottom))
            
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height - paddingBottom)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            
            if (i == dataPoints.size - 1) {
                fillPath.lineTo(x, height - paddingBottom)
                fillPath.close()
            }

            // Dibujar etiquetas del eje X (Días)
            if (dataLabels.isNotEmpty() && i < dataLabels.size) {
                canvas.drawText(dataLabels[i], x, height - 20f, paintText)
            }
        }

        // Dibujar relleno con degradado
        val gradient = LinearGradient(0f, paddingTop, 0f, height - paddingBottom, 
            Color.parseColor("#4000B894"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paintFill.shader = gradient
        canvas.drawPath(fillPath, paintFill)

        // Dibujar línea principal
        canvas.drawPath(path, paintLine)

        // Dibujar puntos
        for (i in dataPoints.indices) {
            val x = paddingLeft + i * xStep
            val normalizedY = (dataPoints[i] - minVal) / range
            val y = height - paddingBottom - (normalizedY * (height - paddingTop - paddingBottom))
            canvas.drawCircle(x, y, 8f, paintDots)
        }
    }
}