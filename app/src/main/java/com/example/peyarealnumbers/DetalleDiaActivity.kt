package com.example.peyarealnumbers

import android.content.Intent
import android.content.res.Configuration as AndroidConfig
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.peyarealnumbers.database.AppDatabase
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

class DetalleDiaActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var tvTiempoTotal: TextView
    private lateinit var tvFecha: TextView
    private lateinit var tvTotalPlana: TextView
    private lateinit var tvTotalReal: TextView
    private lateinit var tvMontoTotal: TextView
    private lateinit var tvPesoPorKm: TextView
    private lateinit var tvEficiencia: TextView
    private lateinit var ivInfoRealKm: ImageView
    private lateinit var ivInfoPagoKm: ImageView
    private lateinit var ivInfoEficiencia: ImageView
    private lateinit var rvSesiones: RecyclerView
    private lateinit var btnShare: ImageButton
    private lateinit var layoutCapture: LinearLayout
    private lateinit var db: AppDatabase
    private var fechaActual: String = ""

    // Colores modernos con transparencia (80% Alpha = CC)
    private val colores = listOf(
        Color.parseColor("#CC007AFF"), // Azul iOS
        Color.parseColor("#CCFF3B30"), // Rojo iOS
        Color.parseColor("#CC4CD964"), // Verde iOS
        Color.parseColor("#CCFF9500"), // Naranja
        Color.parseColor("#CC5856D6"), // Violeta
        Color.parseColor("#CCAF52DE")  // Púrpura
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        setContentView(R.layout.activity_detalle_dia)

        db = AppDatabase.getDatabase(this)
        tvTiempoTotal = findViewById(R.id.tvDetalleTiempoTotal)
        tvFecha = findViewById(R.id.tvDetalleFecha)
        tvTotalPlana = findViewById(R.id.tvDetalleTotalPlana)
        tvTotalReal = findViewById(R.id.tvDetalleTotalReal)
        tvMontoTotal = findViewById(R.id.tvDetalleMontoTotal)
        tvPesoPorKm = findViewById(R.id.tvDetallePesoPorKm)
        tvEficiencia = findViewById(R.id.tvDetalleEficiencia)
        ivInfoRealKm = findViewById(R.id.ivInfoRealKm)
        ivInfoPagoKm = findViewById(R.id.ivInfoPagoKm)
        ivInfoEficiencia = findViewById(R.id.ivInfoEficiencia)
        rvSesiones = findViewById(R.id.rvSesiones)
        map = findViewById(R.id.mapDetalle)
        btnShare = findViewById(R.id.btnShare)
        layoutCapture = findViewById(R.id.layoutCapture)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(16.0)

        // Aplicar modo oscuro al mapa si es necesario
        aplicarModoOscuroMapa()

        fechaActual = intent.getStringExtra("fecha") ?: ""
        
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
        tvFecha.text = try {
            val date = inputFormat.parse(fechaActual)
            outputFormat.format(date!!).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            fechaActual
        }

        observarDatosDia(fechaActual)

        btnShare.setOnClickListener { generarYCompartirReporte() }
        ivInfoRealKm.setOnClickListener { mostrarInfoRealKm() }
        ivInfoPagoKm.setOnClickListener { mostrarInfoPagoKm() }
        ivInfoEficiencia.setOnClickListener { mostrarInfoEficiencia() }
    }

    private fun aplicarModoOscuroMapa() {
        val nightModeFlags = resources.configuration.uiMode and AndroidConfig.UI_MODE_NIGHT_MASK
        if (nightModeFlags == AndroidConfig.UI_MODE_NIGHT_YES) {
            val inverseMatrix = ColorMatrix(floatArrayOf(
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            ))
            val destination = ColorMatrix()
            val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
            val grayscaleMatrix = ColorMatrix(floatArrayOf(
                lr, lg, lb, 0f, 0f,
                lr, lg, lb, 0f, 0f,
                lr, lg, lb, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            destination.setConcat(inverseMatrix, grayscaleMatrix)
            map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
        }
    }

    private fun mostrarInfoRealKm() {
        AlertDialog.Builder(this).setTitle("¿Qué es el Km Real?").setMessage("Es la distancia exacta recorrida considerando las variaciones de altitud (3D).").setPositiveButton("Entendido", null).show()
    }

    private fun mostrarInfoPagoKm() {
        AlertDialog.Builder(this).setTitle("Pago por Km Real").setMessage("Representa tu ganancia neta dividida por los kilómetros reales.").setPositiveButton("Entendido", null).show()
    }

    private fun mostrarInfoEficiencia() {
        AlertDialog.Builder(this).setTitle("Eficiencia de Auditoría").setMessage("Indica el porcentaje de distancia adicional recorrida por pendientes y curvas.").setPositiveButton("Entendido", null).show()
    }

    private fun generarYCompartirReporte() {
        val bitmap = Bitmap.createBitmap(layoutCapture.width, layoutCapture.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        layoutCapture.draw(canvas)
        try {
            val cachePath = File(cacheDir, "images"); cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/reporte_peya.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); stream.close()
            val contentUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(cachePath, "reporte_peya.png"))
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/png"
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir Reporte"))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun observarDatosDia(fecha: String) {
        lifecycleScope.launch {
            db.jornadaDao().getSesionesDelDia(fecha).collect { sesiones ->
                if (sesiones.isEmpty()) { finish(); return@collect }
                var dPlana = 0.0; var dReal = 0.0; var ganancia = 0; var propina = 0; var tiempo = 0L
                for (s in sesiones) {
                    dPlana += s.distanciaPlanaM; dReal += s.distanciaRealM
                    ganancia += s.ganancia; propina += s.propina; tiempo += s.duracionSeg
                }
                tvTotalPlana.text = String.format("Oficial: %.2fkm", dPlana / 1000.0)
                tvTotalReal.text = String.format("%.2f km", dReal / 1000.0)
                tvMontoTotal.text = "$${ganancia + propina}"
                tvTiempoTotal.text = String.format("Tiempo: %02d:%02d:%02d", tiempo / 3600, (tiempo % 3600) / 60, tiempo % 60)
                tvPesoPorKm.text = if (dReal > 0) String.format("$%.1f", (ganancia + propina) / (dReal / 1000.0)) else "$0"
                tvEficiencia.text = if (dPlana > 0) String.format("%.1f%%", ((dReal - dPlana) / dPlana) * 100) else "0%"

                val listaItems = sesiones.mapIndexed { index, s ->
                    SesionItem(s.id, s.numeroSesion, s.nombrePersonalizado ?: "Jornada ${s.numeroSesion}", String.format("%.2f km", s.distanciaRealM / 1000.0), s.ganancia, s.propina, colores[index % colores.size])
                }
                rvSesiones.layoutManager = LinearLayoutManager(this@DetalleDiaActivity)
                rvSesiones.adapter = SesionesAdapter(listaItems) { mostrarOpcionesSesion(it) }

                val archivoGpx = File(getExternalFilesDir(null), "jornada_$fecha.gpx")
                if (archivoGpx.exists()) dibujarRutasBackground(archivoGpx)
            }
        }
    }

    private fun dibujarRutasBackground(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val gpxContent = file.readText()
                val rawSegments = gpxContent.split("<trkseg>")
                val overlaysAMostrar = mutableListOf<org.osmdroid.views.overlay.Overlay>()
                var primerPuntoGlobal: GeoPoint? = null
                var ultimoPuntoGlobal: GeoPoint? = null

                val pattern = Pattern.compile("lat=\"([^\"]+)\"\\s+lon=\"([^\"]+)\"")

                for (i in 1 until rawSegments.size) {
                    val segmentBody = rawSegments[i].substringBefore("</trkseg>")
                    val points = mutableListOf<GeoPoint>()
                    val matcher = pattern.matcher(segmentBody)
                    
                    while (matcher.find()) {
                        val latStr = matcher.group(1).replace(",", ".")
                        val lonStr = matcher.group(2).replace(",", ".")
                        try {
                            val gp = GeoPoint(latStr.toDouble(), lonStr.toDouble())
                            points.add(gp)
                            if (primerPuntoGlobal == null) primerPuntoGlobal = gp
                        } catch (e: Exception) {}
                    }

                    if (points.isNotEmpty()) {
                        val polyline = Polyline(map)
                        polyline.outlinePaint.color = colores[(i - 1) % colores.size]
                        polyline.outlinePaint.strokeWidth = 10f
                        polyline.outlinePaint.strokeCap = Paint.Cap.ROUND
                        
                        val finalPoints = if (points.size > 300) simplificarPuntos(points, 0.000008) else points
                        polyline.setPoints(finalPoints)
                        overlaysAMostrar.add(polyline)
                        ultimoPuntoGlobal = finalPoints.last()
                    }
                }

                withContext(Dispatchers.Main) {
                    val currentOverlays = map.overlays
                    currentOverlays.removeAll(currentOverlays.filter { it is Polyline || it is Marker })
                    currentOverlays.addAll(overlaysAMostrar)
                    
                    if (primerPuntoGlobal != null) {
                        val startMarker = Marker(map)
                        startMarker.position = primerPuntoGlobal
                        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        startMarker.icon = AppCompatResources.getDrawable(this@DetalleDiaActivity, android.R.drawable.presence_online)
                        startMarker.title = "Inicio del recorrido"
                        currentOverlays.add(startMarker)
                    }

                    if (ultimoPuntoGlobal != null) {
                        val endMarker = Marker(map)
                        endMarker.position = ultimoPuntoGlobal
                        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        endMarker.icon = AppCompatResources.getDrawable(this@DetalleDiaActivity, android.R.drawable.ic_menu_mylocation)
                        currentOverlays.add(endMarker)
                        map.controller.animateTo(ultimoPuntoGlobal)
                    }
                    map.invalidate()
                }
            } catch (e: Exception) { Log.e("DetalleDia", "Error: ${e.message}") }
        }
    }

    private fun simplificarPuntos(puntos: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
        if (puntos.size < 3) return puntos
        var dMax = 0.0; var index = 0; val final = puntos.size - 1
        for (i in 1 until final) {
            val d = distanciaPerpendicular(puntos[i], puntos[0], puntos[final])
            if (d > dMax) { index = i; dMax = d }
        }
        return if (dMax > epsilon) {
            val rec1 = simplificarPuntos(puntos.subList(0, index + 1), epsilon)
            val rec2 = simplificarPuntos(puntos.subList(index, puntos.size), epsilon)
            rec1.dropLast(1) + rec2
        } else listOf(puntos[0], puntos[final])
    }

    private fun distanciaPerpendicular(p: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val dx = end.longitude - start.longitude; val dy = end.latitude - start.latitude
        if (dx == 0.0 && dy == 0.0) return Math.sqrt(Math.pow(p.longitude - start.longitude, 2.0) + Math.pow(p.latitude - start.latitude, 2.0))
        val t = ((p.longitude - start.longitude) * dx + (p.latitude - start.latitude) * dy) / (dx * dx + dy * dy)
        return when {
            t < 0.0 -> Math.sqrt(Math.pow(p.longitude - start.longitude, 2.0) + Math.pow(p.latitude - start.latitude, 2.0))
            t > 1.0 -> Math.sqrt(Math.pow(p.longitude - end.longitude, 2.0) + Math.pow(p.latitude - end.latitude, 2.0))
            else -> Math.sqrt(Math.pow(p.longitude - (start.longitude + t * dx), 2.0) + Math.pow(p.latitude - (start.latitude + t * dy), 2.0))
        }
    }

    private fun mostrarOpcionesSesion(item: SesionItem) {
        val ops = arrayOf("Editar jornada", "Eliminar jornada")
        AlertDialog.Builder(this).setTitle("Opciones: ${item.nombre}").setItems(ops) { _, which ->
            if (which == 0) mostrarDialogoEdicion(item) else mostrarConfirmarEliminarSesion(item)
        }.show()
    }

    private fun mostrarDialogoEdicion(item: SesionItem) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_sesion, null)
        val etNombre = view.findViewById<EditText>(R.id.etEditNombre)
        val etGanancia = view.findViewById<EditText>(R.id.etEditGanancia)
        val etPropina = view.findViewById<EditText>(R.id.etEditPropina)
        etNombre.setText(if (item.nombre.startsWith("Jornada ")) "" else item.nombre)
        etGanancia.setText(item.ganancia.toString()); etPropina.setText(item.propina.toString())
        AlertDialog.Builder(this).setTitle("Editar").setView(view).setPositiveButton("Guardar") { _, _ ->
            val g = etGanancia.text.toString().toIntOrNull() ?: 0
            val p = etPropina.text.toString().toIntOrNull() ?: 0
            val n = etNombre.text.toString().ifBlank { null }
            lifecycleScope.launch { db.jornadaDao().editarSesion(item.dbId, g, p, n, fechaActual) }
        }.setNegativeButton("Cancelar", null).show()
    }

    private fun mostrarConfirmarEliminarSesion(item: SesionItem) {
        AlertDialog.Builder(this).setTitle("Eliminar").setMessage("¿Borrar '${item.nombre}'?").setPositiveButton("Eliminar") { _, _ ->
            lifecycleScope.launch { db.jornadaDao().eliminarSesionYRecalcular(item.dbId, fechaActual) }
        }.setNegativeButton("Cancelar", null).show()
    }

    data class SesionItem(val dbId: Long, val numero: Int, val nombre: String, val distancia: String, val ganancia: Int, val propina: Int, val color: Int)

    class SesionesAdapter(private val sesiones: List<SesionItem>, private val onLongClick: (SesionItem) -> Unit) : RecyclerView.Adapter<SesionesAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvId: TextView = v.findViewById(R.id.tvSesionId); val tvDist: TextView = v.findViewById(R.id.tvSesionDist)
            val tvPlata: TextView = v.findViewById(R.id.tvSesionPlata); val viewColor: View = v.findViewById(R.id.viewSesionColor)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sesion, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = sesiones[position]
            holder.tvId.text = item.nombre; holder.tvDist.text = item.distancia; holder.tvPlata.text = "$${item.ganancia + item.propina}"
            holder.viewColor.setBackgroundColor(item.color)
            
            // Ayuda visual al tocar simple
            holder.itemView.setOnClickListener {
                Toast.makeText(it.context, "Mantenga presionado para editar", Toast.LENGTH_SHORT).show()
            }
            
            holder.itemView.setOnLongClickListener { onLongClick(item); true }
        }
        override fun getItemCount() = sesiones.size
    }
}
