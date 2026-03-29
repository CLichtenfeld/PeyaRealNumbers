package com.example.peyarealnumbers

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.database.JornadaEntity
import kotlinx.coroutines.launch

class HistorialActivity : AppCompatActivity() {

    private lateinit var rvHistorial: RecyclerView
    private lateinit var tvTrendAvg: TextView
    private lateinit var trendChart: TrendChartView
    private lateinit var btnBack: ImageButton
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        db = AppDatabase.getDatabase(this)
        rvHistorial = findViewById(R.id.rvHistorial)
        tvTrendAvg = findViewById(R.id.tvTrendAvg)
        trendChart = findViewById(R.id.trendChart)
        btnBack = findViewById(R.id.btnBackHistorial)

        btnBack.setOnClickListener { finish() }

        rvHistorial.layoutManager = LinearLayoutManager(this)
        
        cargarHistorial()
    }

    private fun cargarHistorial() {
        lifecycleScope.launch {
            db.jornadaDao().getAllJornadas().collect { jornadas ->
                val adapter = HistorialAdapter(jornadas) { jornada ->
                    val intent = Intent(this@HistorialActivity, DetalleDiaActivity::class.java)
                    intent.putExtra("fecha", jornada.fecha)
                    startActivity(intent)
                }
                rvHistorial.adapter = adapter
                actualizarGraficoTendencia(jornadas)
            }
        }
    }

    private fun actualizarGraficoTendencia(jornadas: List<JornadaEntity>) {
        if (jornadas.isEmpty()) return
        
        val puntos = jornadas.take(30).reversed().map { j ->
            val pagoKm = if (j.distanciaRealTotal > 0) (j.gananciaTotal + j.propinaTotal) / (j.distanciaRealTotal / 1000.0) else 0.0
            pagoKm.toFloat()
        }
        
        if (puntos.isNotEmpty()) {
            val avg = puntos.average()
            tvTrendAvg.text = String.format("AVG: $%.1f", avg)
            trendChart.setData(puntos)
        }
    }

    class HistorialAdapter(private val jornadas: List<JornadaEntity>, private val onClick: (JornadaEntity) -> Unit) :
        RecyclerView.Adapter<HistorialAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFecha: TextView = view.findViewById(R.id.tvHistorialFecha)
            val tvJornadas: TextView = view.findViewById(R.id.tvHistorialJornadas)
            val tvMonto: TextView = view.findViewById(R.id.tvHistorialMonto)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_historial, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val jornada = jornadas[position]
            holder.tvFecha.text = jornada.fecha
            holder.tvJornadas.text = "${jornada.cantSesiones} jornadas"
            holder.tvMonto.text = "$${jornada.gananciaTotal + jornada.propinaTotal}"
            holder.itemView.setOnClickListener { onClick(jornada) }
        }

        override fun getItemCount() = jornadas.size
    }
}
