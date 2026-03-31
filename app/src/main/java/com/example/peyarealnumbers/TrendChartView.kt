package com.example.peyarealnumbers

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Locale

class TrendChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPointsPago: List<Float> = emptyList()
    private var dataPointsEsfuerzo: List<Float> = emptyList()
    private var dataLabels: List<String> = emptyList()
    private var averagePago: Float? = null
    
    private val paintLinePago = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B894")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val paintFillPago = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintLineEsfuerzo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF7675")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val paintAvgLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B2BEC3")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B2BEC3")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    fun setData(puntosPago: List<Float>, puntosEsfuerzo: List<Float>, labels: List<String> = emptyList(), avg: Float? = null) {
        this.dataPointsPago = puntosPago
        this.dataPointsEsfuerzo = puntosEsfuerzo
        this.dataLabels = labels
        this.averagePago = avg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        
        if (dataPointsPago.size < 2) {
            paintText.textSize = 32f
            canvas.drawText("No hay datos suficientes", width / 2, height / 2, paintText)
            return
        }

        val paddingLeft = 100f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 100f
        
        val maxPago = dataPointsPago.maxOrNull()?.let { if (it == 0f) 1f else it } ?: 1f
        val minPago = dataPointsPago.minOrNull() ?: 0f
        
        // Ajustar el rango para incluir la línea de promedio si existe
        var effectiveMax = maxPago
        var effectiveMin = minPago
        averagePago?.let {
            if (it > effectiveMax) effectiveMax = it * 1.1f
            if (it < effectiveMin) effectiveMin = it * 0.9f
        }
        
        val rangePago = if (effectiveMax == effectiveMin) 1f else effectiveMax - effectiveMin

        val maxEsfuerzo = dataPointsEsfuerzo.maxOrNull()?.let { if (it == 0f) 1f else it } ?: 1f
        val minEsfuerzo = dataPointsEsfuerzo.minOrNull() ?: 0f
        val rangeEsfuerzo = if (maxEsfuerzo == minEsfuerzo) 1f else maxEsfuerzo - minEsfuerzo

        // Dibujar Grid
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = paddingTop + i * (height - paddingTop - paddingBottom) / gridLines
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, paintGrid)
        }

        val xStep = (width - paddingLeft - paddingRight) / (dataPointsPago.size - 1)

        // 1. DIBUJAR LÍNEA DE ESFUERZO (Fondo)
        val pathEsfuerzo = Path()
        for (i in dataPointsEsfuerzo.indices) {
            val x = paddingLeft + i * xStep
            val normalizedY = (dataPointsEsfuerzo[i] - minEsfuerzo) / rangeEsfuerzo
            val y = height - paddingBottom - (normalizedY * (height - paddingTop - paddingBottom))
            if (i == 0) pathEsfuerzo.moveTo(x, y) else pathEsfuerzo.lineTo(x, y)
        }
        canvas.drawPath(pathEsfuerzo, paintLineEsfuerzo)

        // 2. DIBUJAR LÍNEA DE PROMEDIO (Referencia)
        averagePago?.let { avg ->
            val normalizedY = (avg - effectiveMin) / rangePago
            val y = height - paddingBottom - (normalizedY * (height - paddingTop - paddingBottom))
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, paintAvgLine)
            
            paintText.textAlign = Paint.Align.RIGHT
            paintText.textSize = 20f
            canvas.drawText(String.format(Locale.getDefault(), "AVG: $%.1f", avg), width - paddingRight, y - 10f, paintText)
        }

        // 3. DIBUJAR LÍNEA DE PAGO (Frente)
        val pathPago = Path()
        val fillPathPago = Path()
        for (i in dataPointsPago.indices) {
            val x = paddingLeft + i * xStep
            val normalizedY = (dataPointsPago[i] - effectiveMin) / rangePago
            val y = height - paddingBottom - (normalizedY * (height - paddingTop - paddingBottom))
            
            if (i == 0) {
                pathPago.moveTo(x, y)
                fillPathPago.moveTo(x, height - paddingBottom)
                fillPathPago.lineTo(x, y)
            } else {
                pathPago.lineTo(x, y)
                fillPathPago.lineTo(x, y)
            }
            
            if (i == dataPointsPago.size - 1) {
                fillPathPago.lineTo(x, height - paddingBottom)
                fillPathPago.close()
            }

            if (dataLabels.isNotEmpty() && i < dataLabels.size && dataLabels[i].isNotEmpty()) {
                canvas.save()
                paintText.textAlign = Paint.Align.RIGHT
                paintText.textSize = 22f
                // Rotar 45 grados en diagonal
                canvas.rotate(-45f, x, height - 30f)
                canvas.drawText(dataLabels[i], x, height - 30f, paintText)
                canvas.restore()
            }
        }

        val gradient = LinearGradient(0f, paddingTop, 0f, height - paddingBottom, 
            Color.parseColor("#4000B894"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paintFillPago.shader = gradient
        canvas.drawPath(fillPathPago, paintFillPago)
        canvas.drawPath(pathPago, paintLinePago)

        paintText.textAlign = Paint.Align.LEFT
        paintText.textSize = 24f
        canvas.drawText(String.format(Locale.getDefault(), "$%.0f", effectiveMax), 10f, paddingTop + 10f, paintText)
        canvas.drawText(String.format(Locale.getDefault(), "$%.0f", effectiveMin), 10f, height - paddingBottom, paintText)
    }
}