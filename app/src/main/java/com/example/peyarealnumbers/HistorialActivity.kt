package com.example.peyarealnumbers

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.database.JornadaEntity
import com.example.peyarealnumbers.utils.FormatUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistorialActivity : AppCompatActivity() {

    private lateinit var rvHistorial: RecyclerView
    private lateinit var layoutNoData: LinearLayout
    private lateinit var tvTrendAvg: TextView
    private lateinit var tvTrendTitle: TextView
    private lateinit var tvResumenTitle: TextView
    private lateinit var trendChart: TrendChartView
    private lateinit var btnBack: ImageButton
    private lateinit var btnShare: ImageButton
    
    private lateinit var tvSemanaMonto: TextView
    private lateinit var tvSemanaEficiencia: TextView
    private lateinit var tvSemanaInactividad: TextView

    private lateinit var tvRecordGanancia: TextView
    private lateinit var tvRecordEficiencia: TextView
    private lateinit var tvRecordDistancia: TextView

    private lateinit var cardRecordGanancia: CardView
    private lateinit var cardRecordEficiencia: CardView
    private lateinit var cardRecordDistancia: CardView

    private lateinit var tvTabSemana: TextView
    private lateinit var tvTabMes: TextView
    private lateinit var tvTabAnio: TextView
    
    private lateinit var db: AppDatabase
    private var allJornadas: List<JornadaEntity> = emptyList()
    private var currentFilter = "SEMANA"

    private var recordGananciaJornada: JornadaEntity? = null
    private var recordEficienciaJornada: JornadaEntity? = null
    private var recordDistanciaJornada: JornadaEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        db = AppDatabase.getDatabase(this)
        vincularVistas()
        configurarListeners()

        rvHistorial.layoutManager = LinearLayoutManager(this)
        rvHistorial.setHasFixedSize(true)
        
        cargarHistorial()
    }

    private fun vincularVistas() {
        rvHistorial = findViewById(R.id.rvHistorial)
        layoutNoData = findViewById(R.id.layoutNoData)
        tvTrendAvg = findViewById(R.id.tvTrendAvg)
        tvTrendTitle = findViewById(R.id.tvTrendTitle)
        tvResumenTitle = findViewById(R.id.tvResumenTitle)
        trendChart = findViewById(R.id.trendChart)
        btnBack = findViewById(R.id.btnBackHistorial)
        btnShare = findViewById(R.id.btnShareHistorial)
        
        tvSemanaMonto = findViewById(R.id.tvSemanaMonto)
        tvSemanaEficiencia = findViewById(R.id.tvSemanaEficiencia)
        tvSemanaInactividad = findViewById(R.id.tvSemanaInactividad)

        tvRecordGanancia = findViewById(R.id.tvRecordGanancia)
        tvRecordEficiencia = findViewById(R.id.tvRecordEficiencia)
        tvRecordDistancia = findViewById(R.id.tvRecordDistancia)

        cardRecordGanancia = findViewById(R.id.cardRecordGanancia)
        cardRecordEficiencia = findViewById(R.id.cardRecordEficiencia)
        cardRecordDistancia = findViewById(R.id.cardRecordDistancia)

        tvTabSemana = findViewById(R.id.tvTabSemana)
        tvTabMes = findViewById(R.id.tvTabMes)
        tvTabAnio = findViewById(R.id.tvTabAnio)
    }

    private fun configurarListeners() {
        btnBack.setOnClickListener { finish() }
        btnShare.setOnClickListener { compartirHistorial() }

        tvTabSemana.setOnClickListener { currentFilter = "SEMANA"; refrescarUi() }
        tvTabMes.setOnClickListener { currentFilter = "MES"; refrescarUi() }
        tvTabAnio.setOnClickListener { currentFilter = "ANIO"; refrescarUi() }

        cardRecordGanancia.setOnClickListener { mostrarFechaRecord("Mayor Ganancia", recordGananciaJornada) }
        cardRecordEficiencia.setOnClickListener { mostrarFechaRecord("Mejor Eficiencia", recordEficienciaJornada) }
        cardRecordDistancia.setOnClickListener { mostrarFechaRecord("Mayor Distancia", recordDistanciaJornada) }
    }

    private fun compartirHistorial() {
        if (allJornadas.isEmpty()) return
        val limit = when(currentFilter) { "SEMANA" -> 7; "MES" -> 30; "ANIO" -> 365; else -> 30 }
        val shareText = """
            📊 ${if (currentFilter == "ANIO") "MI RESUMEN ANUAL" else "MI RESUMEN ÚLTIMOS $limit DÍAS"}
            💰 Total: ${tvSemanaMonto.text}
            ⚡ Eficiencia: ${tvSemanaEficiencia.text}
            ⏱️ Inactividad: ${tvSemanaInactividad.text}
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Compartir resumen"))
    }

    private fun refrescarUi() {
        updateTabsUi()
        actualizarResumenDinamico(allJornadas)
        actualizarGraficoTendencia(allJornadas)
        actualizarListaFiltrada()
    }

    private fun updateTabsUi() {
        val activeColor = Color.BLACK
        val inactiveColor = Color.parseColor("#B2BEC3")
        tvTabSemana.setTextColor(if (currentFilter == "SEMANA") activeColor else inactiveColor)
        tvTabMes.setTextColor(if (currentFilter == "MES") activeColor else inactiveColor)
        tvTabAnio.setTextColor(if (currentFilter == "ANIO") activeColor else inactiveColor)
    }

    private fun cargarHistorial() {
        lifecycleScope.launch {
            db.jornadaDao().getAllJornadas().collect { jornadas ->
                allJornadas = jornadas
                actualizarRecords(jornadas)
                refrescarUi()
            }
        }
    }

    private fun mostrarFechaRecord(titulo: String, jornada: JornadaEntity?) {
        jornada?.let {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.fecha)
            val fechaFormateada = SimpleDateFormat("EEE, d 'de' MMMM", Locale("es", "ES")).format(date!!)
            Toast.makeText(this, "$titulo: $fechaFormateada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actualizarListaFiltrada() {
        val limit = when(currentFilter) { "SEMANA" -> 7; "MES" -> 30; "ANIO" -> 365; else -> 30 }
        val jornadasFiltradas = allJornadas.take(limit)
        
        layoutNoData.visibility = if (jornadasFiltradas.isEmpty()) View.VISIBLE else View.GONE
        rvHistorial.visibility = if (jornadasFiltradas.isEmpty()) View.GONE else View.VISIBLE
        
        if (jornadasFiltradas.isNotEmpty()) {
            rvHistorial.adapter = HistorialAdapter(jornadasFiltradas) { j ->
                startActivity(Intent(this, DetalleDiaActivity::class.java).apply { putExtra("fecha", j.fecha) })
            }
        }
    }

    private fun actualizarResumenDinamico(jornadas: List<JornadaEntity>) {
        if (jornadas.isEmpty()) return
        val limit = when(currentFilter) { "SEMANA" -> 7; "MES" -> 30; "ANIO" -> 365; else -> 30 }
        tvResumenTitle.text = if (currentFilter == "ANIO") "RESUMEN TOTAL" else "RESUMEN ÚLTIMOS $limit DÍAS"

        val jornadasPeriodo = jornadas.take(limit)
        var monto = 0; var dist = 0.0; var tTotal = 0L; var tVacio = 0L
        for (j in jornadasPeriodo) {
            monto += (j.gananciaTotal + j.propinaTotal)
            dist += j.distanciaRealTotal
            tTotal += j.tiempoTotalSegundos
            tVacio += j.tiempoVacioTotalSeg
        }
        tvSemanaMonto.text = "$$monto"
        tvSemanaInactividad.text = "${if (tTotal > 0) (tVacio.toFloat() / tTotal * 100).toInt() else 0}%"
        tvSemanaEficiencia.text = if (dist > 0.1) String.format(Locale.getDefault(), "$%.1f/km", monto / dist) else "$0/km"
    }

    private fun actualizarRecords(jornadas: List<JornadaEntity>) {
        if (jornadas.isEmpty()) return
        var maxG = 0; var maxE = 0f; var maxD = 0.0
        for (j in jornadas) {
            val total = j.gananciaTotal + j.propinaTotal
            if (total >= maxG) { maxG = total; recordGananciaJornada = j }
            if (j.distanciaRealTotal > 0) {
                val ef = total / j.distanciaRealTotal.toFloat()
                if (ef >= maxE) { maxE = ef; recordEficienciaJornada = j }
            }
            if (j.distanciaRealTotal >= maxD) { maxD = j.distanciaRealTotal; recordDistanciaJornada = j }
        }
        tvRecordGanancia.text = "$$maxG"
        tvRecordEficiencia.text = String.format(Locale.getDefault(), "$%.1f/km", maxE)
        tvRecordDistancia.text = String.format(Locale.getDefault(), "%.1fkm", maxD)
    }

    private fun actualizarGraficoTendencia(jornadas: List<JornadaEntity>) {
        if (jornadas.isEmpty()) { trendChart.setData(emptyList(), emptyList()); return }
        val limit = when(currentFilter) { "SEMANA" -> 7; "MES" -> 30; "ANIO" -> 365; else -> 30 }
        tvTrendTitle.text = "TENDENCIA $/KM (ÚLTIMOS $limit DÍAS)"
        
        val filtradas = jornadas.filter { it.distanciaRealTotal > 0 }.take(limit).reversed()
        if (filtradas.isEmpty()) { trendChart.setData(emptyList(), emptyList()); return }

        val pPago = filtradas.map { ((it.gananciaTotal + it.propinaTotal) / it.distanciaRealTotal).toFloat() }
        val pEsfuerzo = filtradas.map { (it.joulesTotales / 4184.0 / it.distanciaRealTotal).toFloat() }
        
        val totalMonto = filtradas.sumOf { (it.gananciaTotal + it.propinaTotal).toDouble() }
        val totalDist = filtradas.sumOf { it.distanciaRealTotal }
        val avg = if (totalDist > 0.01) (totalMonto / totalDist).toFloat() else 0f
        
        val labels = mutableListOf<String>()
        for (j in filtradas) {
            val date = try { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(j.fecha)!! } catch(e: Exception) { Date() }
            val cal = Calendar.getInstance().apply { time = date }
            val day = cal.get(Calendar.DAY_OF_MONTH)
            
            val label = when {
                limit <= 7 -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
                limit == 30 -> if (day % 5 == 0) SimpleDateFormat("dd/MM", Locale.getDefault()).format(date) else ""
                else -> { // Caso ANIO (365)
                    // Mostrar etiqueta el 10 de cada mes
                    if (day == 10) SimpleDateFormat("MMM", Locale("es", "ES")).format(date).uppercase() else ""
                }
            }
            labels.add(label)
        }

        tvTrendAvg.text = String.format("AVG PAGO: $%.1f", avg)
        trendChart.setData(pPago, pEsfuerzo, labels, avg)
    }

    class HistorialAdapter(private val jornadas: List<JornadaEntity>, private val onClick: (JornadaEntity) -> Unit) :
        RecyclerView.Adapter<HistorialAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvFecha: TextView = v.findViewById(R.id.tvHistorialFecha)
            val tvJornadas: TextView = v.findViewById(R.id.tvHistorialJornadas)
            val tvMonto: TextView = v.findViewById(R.id.tvHistorialMonto)
            val tvExtraInfo: TextView = v.findViewById(R.id.tvHistorialExtraInfo)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_historial, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val j = jornadas[pos]
            h.tvFecha.text = try { val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(j.fecha); SimpleDateFormat("EEE, d MMM", Locale("es", "ES")).format(d!!).replaceFirstChar { it.uppercase() } } catch (e: Exception) { j.fecha }
            h.tvJornadas.text = "${j.cantSesiones} sesiones"
            h.tvMonto.text = "$${j.gananciaTotal + j.propinaTotal}"
            val pInac = if (j.tiempoTotalSegundos > 0) (j.tiempoVacioTotalSeg.toFloat() / j.tiempoTotalSegundos * 100).toInt() else 0
            h.tvExtraInfo.text = "${FormatUtils.formatDistance(j.distanciaRealTotal * 1000)} | $pInac% inactivo"
            h.itemView.setOnClickListener { onClick(j) }
        }
        override fun getItemCount() = jornadas.size
    }
}
