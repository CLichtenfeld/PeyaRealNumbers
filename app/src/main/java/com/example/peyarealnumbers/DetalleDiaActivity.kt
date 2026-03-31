package com.example.peyarealnumbers

import android.content.Intent
import android.content.res.Configuration as AndroidConfig
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.database.SesionEntity
import com.example.peyarealnumbers.utils.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Activity que muestra el desglose detallado de una jornada de trabajo específica.
 * Incluye visualización de rutas en mapa (GPX), métricas de esfuerzo y eficiencia.
 */
class DetalleDiaActivity : AppCompatActivity() {

    // Componentes de la Interfaz de Usuario
    private lateinit var map: MapView
    private lateinit var tvTiempoTotal: TextView
    private lateinit var tvFecha: TextView
    private lateinit var tvTotalPlana: TextView
    private lateinit var tvTotalReal: TextView
    private lateinit var tvMontoTotal: TextView
    private lateinit var tvPesoPorKm: TextView
    private lateinit var tvEsfuerzoExtra: TextView
    private lateinit var tvTotalKcal: TextView
    private lateinit var ivInfoRealKm: ImageView
    private lateinit var ivInfoPagoKm: ImageView
    private lateinit var ivInfoExtra: ImageView
    private lateinit var rvSesiones: RecyclerView
    private lateinit var btnShare: ImageButton
    private lateinit var layoutCapture: LinearLayout

    // Estado y Datos
    private lateinit var db: AppDatabase
    private var fechaActual: String = ""
    private val coloresRutas = listOf(
        Color.parseColor("#CC007AFF"), // Azul
        Color.parseColor("#CCFF3B30"), // Rojo
        Color.parseColor("#CC4CD964"), // Verde
        Color.parseColor("#CCFF9500"), // Naranja
        Color.parseColor("#CC5856D6"), // Índigo
        Color.parseColor("#CCAF52DE")  // Púrpura
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // La configuración de OSMDroid debe realizarse antes de inflar el layout
        setupMapConfig()
        
        setContentView(R.layout.activity_detalle_dia)

        db = AppDatabase.getDatabase(this)
        vincularVistas()
        setupInitialMapState()

        fechaActual = intent.getStringExtra("fecha") ?: ""
        configurarFechaCabecera()
        
        // Iniciamos la observación reactiva de los datos
        observarDatosJornada(fechaActual)
        configurarListeners()
    }

    private fun setupMapConfig() {
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
    }

    private fun setupInitialMapState() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(16.0)
        aplicarModoOscuroMapa()
    }

    private fun vincularVistas() {
        tvTiempoTotal = findViewById(R.id.tvDetalleTiempoTotal)
        tvFecha = findViewById(R.id.tvDetalleFecha)
        tvTotalPlana = findViewById(R.id.tvDetalleTotalPlana)
        tvTotalReal = findViewById(R.id.tvDetalleTotalReal)
        tvMontoTotal = findViewById(R.id.tvDetalleMontoTotal)
        tvPesoPorKm = findViewById(R.id.tvDetallePesoPorKm)
        tvEsfuerzoExtra = findViewById(R.id.tvDetalleEsfuerzoExtra)
        tvTotalKcal = findViewById(R.id.tvDetalleTotalKcal)
        ivInfoRealKm = findViewById(R.id.ivInfoRealKm)
        ivInfoPagoKm = findViewById(R.id.ivInfoPagoKm)
        ivInfoExtra = findViewById(R.id.ivInfoExtra)
        rvSesiones = findViewById(R.id.rvSesiones)
        map = findViewById(R.id.mapDetalle)
        btnShare = findViewById(R.id.btnShare)
        layoutCapture = findViewById(R.id.layoutCapture)
    }

    private fun configurarListeners() {
        btnShare.setOnClickListener { generarYCompartirReporte() }
        
        // Diálogos informativos para métricas complejas
        ivInfoRealKm.setOnClickListener { 
            mostrarInfo("Distancia Real", "Calculada mediante altimetría 3D para una precisión exacta del recorrido.") 
        }
        ivInfoPagoKm.setOnClickListener { 
            mostrarInfo("Rentabilidad", "Ingreso neto promedio obtenido por cada kilómetro real recorrido.") 
        }
        ivInfoExtra.setOnClickListener { 
            mostrarInfo("Esfuerzo Extra", "Porcentaje de energía adicional consumida debido a pendientes y elevación.") 
        }
    }

    /**
     * Observa los cambios en la base de datos para la jornada actual.
     */
    private fun observarDatosJornada(fecha: String) {
        lifecycleScope.launch {
            db.jornadaDao().getSesionesDelDia(fecha).collect { sesiones ->
                if (sesiones.isEmpty()) {
                    finish()
                    return@collect
                }
                actualizarMétricasUI(sesiones)
                setupRecyclerView(sesiones)
                dibujarRutasGPX(File(getExternalFilesDir(null), "jornada_$fecha.gpx"))
            }
        }
    }

    private fun actualizarMétricasUI(sesiones: List<SesionEntity>) {
        var dPlanaTotal = 0.0
        var dRealTotal = 0.0
        var ingresosTotales = 0
        var tiempoTotalSeg = 0L
        var pedidosTotales = 0
        var joulesGastados = 0.0
        var joulesBasePlano = 0.0

        for (s in sesiones) {
            dPlanaTotal += s.distanciaPlanaM
            dRealTotal += s.distanciaRealM
            ingresosTotales += (s.ganancia + s.propina)
            tiempoTotalSeg += s.duracionSeg
            pedidosTotales += s.cantPedidos
            joulesGastados += s.joulesTotales
            joulesBasePlano += s.joulesPlanoTotales
        }

        tvTotalPlana.text = String.format("Oficial: %.2fkm", dPlanaTotal / 1000.0)
        tvTotalReal.text = String.format("%.2f km", dRealTotal / 1000.0)
        tvMontoTotal.text = "$$ingresosTotales"
        
        val promedio = if (pedidosTotales > 0) ingresosTotales / pedidosTotales else 0
        tvTiempoTotal.text = "Tiempo: ${FormatUtils.formatElapsedTime(tiempoTotalSeg)} | Pedidos: $pedidosTotales ($$promedio/u)"
        
        tvPesoPorKm.text = if (dRealTotal > 0) String.format("$%.1f", ingresosTotales / (dRealTotal / 1000.0)) else "$0"
        tvTotalKcal.text = (joulesGastados / 4184.0).toInt().toString()
        
        val extraFactor = if (joulesBasePlano > 10) ((joulesGastados / joulesBasePlano) - 1.0) * 100.0 else 0.0
        tvEsfuerzoExtra.text = String.format("+%.0f%%", extraFactor)
    }

    private fun setupRecyclerView(sesiones: List<SesionEntity>) {
        val listaItems = sesiones.mapIndexed { index, s ->
            SesionItem(
                dbId = s.id,
                nombre = s.nombrePersonalizado ?: "Jornada ${s.numeroSesion}",
                distancia = String.format("%.2f km", s.distanciaRealM / 1000.0),
                total = s.ganancia + s.propina,
                pedidos = s.cantPedidos,
                color = coloresRutas[index % coloresRutas.size],
                horario = "${s.horaInicio} - ${s.horaFin ?: "..."}",
                vacioStr = "Vacío: ${FormatUtils.formatElapsedTime(s.tiempoVacioSeg)}"
            )
        }
        rvSesiones.layoutManager = LinearLayoutManager(this)
        rvSesiones.adapter = SesionesAdapter(listaItems) { item -> mostrarOpcionesSesion(item) }
    }

    /**
     * Procesa y visualiza el trayecto almacenado en el archivo GPX.
     */
    private fun dibujarRutasGPX(file: File) {
        if (!file.exists()) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gpxContent = file.readText()
                val rawSegments = gpxContent.split("<trkseg>")
                val overlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                var startPoint: GeoPoint? = null
                var endPoint: GeoPoint? = null

                val pattern = Pattern.compile("lat=\"([^\"]+)\"\\s+lon=\"([^\"]+)\"")

                for (i in 1 until rawSegments.size) {
                    val segmentBody = rawSegments[i].substringBefore("</trkseg>")
                    val points = mutableListOf<GeoPoint>()
                    val matcher = pattern.matcher(segmentBody)
                    
                    while (matcher.find()) {
                        try {
                            val gp = GeoPoint(matcher.group(1).toDouble(), matcher.group(2).toDouble())
                            points.add(gp)
                            if (startPoint == null) startPoint = gp
                        } catch (e: Exception) { /* Omitir puntos inválidos */ }
                    }

                    if (points.isNotEmpty()) {
                        val polyline = Polyline(map)
                        polyline.outlinePaint.color = coloresRutas[(i - 1) % coloresRutas.size]
                        polyline.outlinePaint.strokeWidth = 10f
                        polyline.outlinePaint.strokeCap = Paint.Cap.ROUND
                        
                        // Simplificación del trazado para optimizar el rendimiento del mapa
                        val finalPoints = if (points.size > 300) simplificarPuntos(points, 0.000008) else points
                        polyline.setPoints(finalPoints)
                        overlays.add(polyline)
                        endPoint = finalPoints.last()
                    }
                }

                withContext(Dispatchers.Main) {
                    actualizarMapaUI(overlays, startPoint, endPoint)
                }
            } catch (e: Exception) {
                Log.e("DetalleDia", "Error al procesar GPX: ${e.message}")
            }
        }
    }

    private fun actualizarMapaUI(overlays: List<org.osmdroid.views.overlay.Overlay>, start: GeoPoint?, end: GeoPoint?) {
        map.overlays.clear()
        map.overlays.addAll(overlays)
        
        start?.let {
            val marker = Marker(map)
            marker.position = it
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.icon = AppCompatResources.getDrawable(this, android.R.drawable.presence_online)
            marker.title = "Inicio"
            map.overlays.add(marker)
        }

        end?.let {
            val marker = Marker(map)
            marker.position = it
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = AppCompatResources.getDrawable(this, android.R.drawable.ic_menu_mylocation)
            marker.title = "Fin"
            map.overlays.add(marker)
            map.controller.animateTo(it)
        }
        map.invalidate()
    }

    /**
     * Exporta el dashboard actual como imagen y abre el diálogo de compartir.
     */
    private fun generarYCompartirReporte() {
        val bitmap = Bitmap.createBitmap(layoutCapture.width, layoutCapture.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layoutCapture.draw(canvas)
        
        try {
            val cachePath = File(cacheDir, "images").apply { mkdirs() }
            val stream = FileOutputStream("$cachePath/reporte_peya.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", File(cachePath, "reporte_peya.png")
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/png"
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir Reporte"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al generar el reporte", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Métodos de Apoyo ---

    private fun aplicarModoOscuroMapa() {
        val isNight = (resources.configuration.uiMode and AndroidConfig.UI_MODE_NIGHT_MASK) == AndroidConfig.UI_MODE_NIGHT_YES
        if (isNight) {
            val inverseMatrix = ColorMatrix(floatArrayOf(
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            ))
            map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
        }
    }

    private fun mostrarInfo(titulo: String, mensaje: String) {
        AlertDialog.Builder(this).setTitle(titulo).setMessage(mensaje).setPositiveButton("OK", null).show()
    }

    private fun configurarFechaCabecera() {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
        tvFecha.text = try {
            val date = inputFormat.parse(fechaActual)
            outputFormat.format(date!!).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) { fechaActual }
    }

    private fun simplificarPuntos(puntos: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
        if (puntos.size < 3) return puntos
        var dMax = 0.0
        var index = 0
        val final = puntos.size - 1
        for (i in 1 until final) {
            val d = calcularDistanciaPerpendicular(puntos[i], puntos[0], puntos[final])
            if (d > dMax) { index = i; dMax = d }
        }
        return if (dMax > epsilon) {
            val r1 = simplificarPuntos(puntos.subList(0, index + 1), epsilon)
            val r2 = simplificarPuntos(puntos.subList(index, puntos.size), epsilon)
            r1.dropLast(1) + r2
        } else listOf(puntos[0], puntos[final])
    }

    private fun calcularDistanciaPerpendicular(p: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        if (dx == 0.0 && dy == 0.0) return Math.sqrt(Math.pow(p.longitude - start.longitude, 2.0) + Math.pow(p.latitude - start.latitude, 2.0))
        val t = ((p.longitude - start.longitude) * dx + (p.latitude - start.latitude) * dy) / (dx * dx + dy * dy)
        return when {
            t < 0.0 -> Math.sqrt(Math.pow(p.longitude - start.longitude, 2.0) + Math.pow(p.latitude - start.latitude, 2.0))
            t > 1.0 -> Math.sqrt(Math.pow(p.longitude - end.longitude, 2.0) + Math.pow(p.latitude - end.latitude, 2.0))
            else -> Math.sqrt(Math.pow(p.longitude - (start.longitude + t * dx), 2.0) + Math.pow(p.latitude - (start.latitude + t * dy), 2.0))
        }
    }

    // --- Diálogos de Gestión ---

    private fun mostrarOpcionesSesion(item: SesionItem) {
        val opciones = arrayOf("Editar jornada", "Eliminar jornada")
        AlertDialog.Builder(this).setTitle(item.nombre).setItems(opciones) { _, which ->
            if (which == 0) mostrarDialogoEdicion(item) else mostrarConfirmarEliminar(item)
        }.show()
    }

    private fun mostrarDialogoEdicion(item: SesionItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_sesion, null)
        val etNombre = view.findViewById<EditText>(R.id.etEditNombre)
        val etGanancia = view.findViewById<EditText>(R.id.etEditGanancia)
        val etPropina = view.findViewById<EditText>(R.id.etEditPropina)
        
        val etPedidos = EditText(this).apply {
            hint = "Pedidos"; setText(item.pedidos.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(12, 12, 12, 12)
        }
        (view.findViewById<LinearLayout>(R.id.layoutEditContainer) ?: (view as ViewGroup)).addView(etPedidos)

        etNombre.setText(if (item.nombre.startsWith("Jornada ")) "" else item.nombre)
        etGanancia.setText((item.total).toString()) 
        
        AlertDialog.Builder(this).setTitle("Editar").setView(view).setPositiveButton("Guardar") { _, _ ->
            val g = etGanancia.text.toString().toIntOrNull() ?: 0
            val p = etPropina.text.toString().toIntOrNull() ?: 0
            val c = etPedidos.text.toString().toIntOrNull() ?: item.pedidos
            val n = etNombre.text.toString().ifBlank { null }
            lifecycleScope.launch { db.jornadaDao().editarSesion(item.dbId, g, p, c, n, fechaActual) }
        }.setNegativeButton("Cancelar", null).show()
    }

    private fun mostrarConfirmarEliminar(item: SesionItem) {
        AlertDialog.Builder(this).setTitle("Eliminar")
            .setMessage("¿Confirmas la eliminación de esta jornada?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch { db.jornadaDao().eliminarSesionYRecalcular(item.dbId, fechaActual) }
            }.setNegativeButton("Cancelar", null).show()
    }

    // --- Componentes del RecyclerView ---

    data class SesionItem(
        val dbId: Long, val nombre: String, val distancia: String,
        val total: Int, val pedidos: Int, val color: Int,
        val horario: String, val vacioStr: String
    )

    class SesionesAdapter(
        private val sesiones: List<SesionItem>, 
        private val onLongClick: (SesionItem) -> Unit
    ) : RecyclerView.Adapter<SesionesAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvId: TextView = v.findViewById(R.id.tvSesionId)
            val tvDist: TextView = v.findViewById(R.id.tvSesionDist)
            val tvPlata: TextView = v.findViewById(R.id.tvSesionPlata)
            val tvPedidos: TextView = v.findViewById(R.id.tvSesionPedidos)
            val viewColor: View = v.findViewById(R.id.viewSesionColor)
            val tvHorario: TextView = v.findViewById(R.id.tvSesionHorario)
            val tvVacio: TextView = v.findViewById(R.id.tvSesionVacio)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sesion, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = sesiones[position]
            with(holder) {
                tvId.text = item.nombre
                tvHorario.text = item.horario
                tvDist.text = item.distancia
                tvPlata.text = "$${item.total}"
                tvPedidos.text = "${item.pedidos} pedidos"
                tvVacio.text = item.vacioStr
                viewColor.setBackgroundColor(item.color)
                itemView.setOnLongClickListener { onLongClick(item); true }
            }
        }

        override fun getItemCount() = sesiones.size
    }
}
