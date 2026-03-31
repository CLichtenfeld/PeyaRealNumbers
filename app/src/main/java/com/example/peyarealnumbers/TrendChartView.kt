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
        color = Color.parseColor("#E67E22") // Naranja más visible
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#636E72") // Gris oscuro para mejor legibilidad
        textSize = 22f
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
        if (dataPointsPago.size < 2) return

        val w = width.toFloat()
        val h = height.toFloat()
        val padL = 90f
        val padR = 40f
        val padT = 30f
        val padB = 80f // Espacio para etiquetas
        val cW = w - padL - padR
        val cH = h - padT - padB

        val maxP = dataPointsPago.maxOrNull() ?: 1f
        val minP = dataPointsPago.minOrNull() ?: 0f
        val avgP = averagePago ?: 0f
        
        val top = maxOf(maxP, avgP) * 1.1f
        val bot = minOf(minP, avgP) * 0.9f
        val range = if (top == bot) 1f else top - bot

        // Dibujar Grid (3 líneas horizontales)
        for (i in 0..2) {
            val y = padT + i * cH / 2
            canvas.drawLine(padL, y, w - padR, y, paintGrid)
        }

        val step = cW / (dataPointsPago.size - 1)

        // 1. Dibujar Línea de Esfuerzo
        val pathE = Path()
        val maxE = dataPointsEsfuerzo.maxOrNull() ?: 1f
        val rangeE = if (maxE == 0f) 1f else maxE
        for (i in dataPointsEsfuerzo.indices) {
            val x = padL + i * step
            val y = h - padB - (dataPointsEsfuerzo[i] / rangeE * cH)
            if (i == 0) pathE.moveTo(x, y) else pathE.lineTo(x, y)
        }
        canvas.drawPath(pathE, paintLineEsfuerzo)

        // 2. Dibujar Línea de Pago y Relleno
        val pathP = Path()
        val pathF = Path()
        for (i in dataPointsPago.indices) {
            val x = padL + i * step
            val y = h - padB - ((dataPointsPago[i] - bot) / range * cH)
            if (i == 0) {
                pathP.moveTo(x, y)
                pathF.moveTo(x, h - padB)
                pathF.lineTo(x, y)
            } else {
                pathP.lineTo(x, y)
                pathF.lineTo(x, y)
            }
            if (i == dataPointsPago.size - 1) {
                pathF.lineTo(x, h - padB)
                pathF.close()
            }
        }
        paintFillPago.shader = LinearGradient(0f, padT, 0f, h - padB, 
            Color.parseColor("#3300B894"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawPath(pathF, paintFillPago)
        canvas.drawPath(pathP, paintLinePago)

        // 3. Dibujar Línea de Media (Promedio)
        averagePago?.let { avg ->
            val y = h - padB - ((avg - bot) / range * cH)
            canvas.drawLine(padL, y, w - padR, y, paintAvgLine)
            paintText.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format(Locale.getDefault(), "AVG: $%.1f", avg), w - padR, y - 8f, paintText)
        }

        // 4. Dibujar Etiquetas Eje Y
        paintText.textAlign = Paint.Align.LEFT
        canvas.drawText(String.format(Locale.getDefault(), "$%.0f", top), 5f, padT + 20f, paintText)
        canvas.drawText(String.format(Locale.getDefault(), "$%.0f", bot), 5f, h - padB, paintText)

        // 5. DIBUJAR ETIQUETAS EJE X (MESES) - SE DIBUJAN AL FINAL PARA QUE NADA LAS TAPE
        paintText.textAlign = Paint.Align.CENTER
        paintText.textSize = 20f
        for (i in dataLabels.indices) {
            if (dataLabels.isNotEmpty() && i < dataLabels.size && dataLabels[i].isNotEmpty()) {
                val x = padL + i * step
                // Dibujamos de forma horizontal para que sea simple y claro
                canvas.drawText(dataLabels[i], x, h - 20f, paintText)
            }
        }
    }
}
