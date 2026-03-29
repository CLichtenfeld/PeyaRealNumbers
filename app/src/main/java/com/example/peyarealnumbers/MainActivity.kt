package com.example.peyarealnumbers

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration as AndroidConfig
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.peyarealnumbers.database.AppDatabase
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST = 1001
    private val OVERLAY_PERMISSION_REQUEST = 1002
    private var jornadaActualId = 1

    private lateinit var tvTimer: TextView
    private lateinit var tvJornadaStatus: TextView
    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvTotalPlana: TextView
    private lateinit var tvCurrentAlt: TextView
    private lateinit var tvPace: TextView
    private lateinit var tvTiempoVacio: TextView
    private lateinit var ivInfoPace: ImageView
    private lateinit var btnToggle: Button
    private lateinit var btnCenter: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var btnSupport: ImageButton
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
        setContentView(R.layout.activity_main)
        db = AppDatabase.getDatabase(this)

        vincularVistas()
        configurarMapa()
        observarEstadoTracking()
        verificarPermisoSuperposicion()

        btnToggle.setOnClickListener {
            if (!TrackingManager.uiState.value.isTracking) {
                if (tengoPermisosUbicacion()) {
                    val intent = Intent(this, GpsService::class.java)
                    startForegroundService(intent)
                    actualizarInterfaz(true)
                } else {
                    mostrarExplicacionPermisos()
                }
            } else {
                confirmarCierre()
            }
        }

        btnHistory.setOnClickListener { startActivity(Intent(this, HistorialActivity::class.java)) }
        btnSupport.setOnClickListener { startActivity(Intent(this, SoporteActivity::class.java)) }
        ivInfoPace.setOnClickListener { mostrarInfoRitmo() }
        tvTimer.setOnLongClickListener { confirmarBorradoHoy(); true }
    }

    private fun verificarPermisoSuperposicion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Permiso Necesario")
                    .setMessage("Para poder avisarte cuando llevas tiempo detenido (Tiempo Vacío), necesitamos el permiso de 'Mostrar sobre otras aplicaciones'.\n\nAl pulsar 'Ir a Ajustes', busca 'Peya Real Numbers' en la lista y actívalo.")
                    .setPositiveButton("Ir a Ajustes") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
                    }
                    .setNegativeButton("Ahora no", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        locationOverlay.enableMyLocation()
        if (!tengoPermisosUbicacion()) pedirPermisos()
    }

    private fun tengoPermisosUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fgs = if (Build.VERSION.SDK_INT >= 34) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        val background = if (Build.VERSION.SDK_INT >= 29) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && fgs && background
    }

    private fun mostrarExplicacionPermisos() {
        AlertDialog.Builder(this)
            .setTitle("Permisos de Ubicación")
            .setMessage("Para auditar tus rutas correctamente, necesitamos acceso 'Todo el tiempo'.\n\nPor favor, acepta los permisos en la siguiente pantalla.")
            .setPositiveButton("Configurar") { _, _ -> pedirPermisos() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun pedirPermisos() {
        val basicos = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE)
        if (Build.VERSION.SDK_INT >= 34) basicos.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        val faltanBasicos = basicos.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (faltanBasicos.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltanBasicos.toTypedArray(), PERMISSION_REQUEST)
        } else if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this).setTitle("Permiso 'Todo el tiempo'").setMessage("Selecciona 'Permitir todo el tiempo' para que el rastreo no se detenga al apagar la pantalla.").setPositiveButton("Configurar") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), PERMISSION_REQUEST)
            }.setNegativeButton("Después", null).show()
        }
    }

    private fun confirmarBorradoHoy() {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        AlertDialog.Builder(this).setTitle("Limpiar día de hoy").setMessage("¿Seguro que quieres borrar todas las pruebas de hoy?")
            .setPositiveButton("Sí, borrar") { _, _ ->
                lifecycleScope.launch {
                    db.jornadaDao().eliminarSesionesDelDia(fecha); db.jornadaDao().eliminarJornada(fecha)
                    val gpxFile = File(getExternalFilesDir(null), "jornada_$fecha.gpx")
                    if (gpxFile.exists()) gpxFile.delete()
                    tvTotalPlana.text = "0.00 km"; tvTimer.text = "00:00:00"; tvTiempoVacio.text = "00:00:00"
                    actualizarInterfaz(false)
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun mostrarInfoRitmo() {
        AlertDialog.Builder(this).setTitle("¿Qué es el Ritmo?").setMessage("Es el tiempo promedio que te toma recorrer un kilómetro.").setPositiveButton("Entendido", null).show()
    }

    private fun observarEstadoTracking() {
        lifecycleScope.launch {
            TrackingManager.uiState.collect { state ->
                tvTimer.text = state.tiempoTranscurrido
                tvTiempoVacio.text = state.tiempoVacioStr
                jornadaActualId = state.jornadaId
                tvCurrentSpeed.text = String.format("%.1f", state.speed)
                tvCurrentAlt.text = String.format("%.0fm", state.altitude)
                tvPace.text = state.pace
                actualizarInterfaz(state.isTracking)
                if (state.isTracking) {
                    tvJornadaStatus.text = "Jornada en curso"
                    tvJornadaStatus.visibility = android.view.View.VISIBLE
                    cargarDatosDesdeDb()
                } else {
                    tvJornadaStatus.visibility = android.view.View.INVISIBLE
                }
            }
        }
    }

    private fun vincularVistas() {
        tvTimer = findViewById(R.id.tvTimer)
        tvJornadaStatus = findViewById(R.id.tvJornadaStatus)
        tvCurrentSpeed = findViewById(R.id.tvCurrentSpeed)
        tvTotalPlana = findViewById(R.id.tvTotalPlana)
        tvCurrentAlt = findViewById(R.id.tvCurrentAlt)
        tvPace = findViewById(R.id.tvPace)
        tvTiempoVacio = findViewById(R.id.tvTiempoVacio)
        ivInfoPace = findViewById(R.id.ivInfoPace)
        btnToggle = findViewById(R.id.btnToggle)
        btnCenter = findViewById(R.id.btnCenter)
        btnHistory = findViewById(R.id.btnHistory)
        btnSupport = findViewById(R.id.btnSupport)
    }

    private fun configurarMapa() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        val dotDrawable = ContextCompat.getDrawable(this, R.drawable.red_dot)
        dotDrawable?.let {
            val bitmap = drawableToBitmap(it)
            locationOverlay.setPersonIcon(bitmap)
            locationOverlay.setDirectionIcon(bitmap)
            locationOverlay.setPersonAnchor(0.5f, 0.5f)
            locationOverlay.setDirectionAnchor(0.5f, 0.5f)
        }
        locationOverlay.enableMyLocation(); locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
        map.controller.setZoom(18.0)
        
        aplicarModoOscuroMapa()
        
        btnCenter.setOnClickListener { locationOverlay.myLocation?.let { map.controller.animateTo(it) } }
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
            map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
        }
    }

    private fun cargarDatosDesdeDb() {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch {
            db.jornadaDao().getJornadaPorFecha(fecha)?.let {
                tvTotalPlana.text = String.format("%.2f km", it.distanciaPlanaTotal)
            }
        }
    }

    private fun actualizarInterfaz(isCorriendo: Boolean) {
        if (isCorriendo) {
            btnToggle.text = "DETENER RUTA"
            btnToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
        } else {
            btnToggle.text = "INICIAR RUTA"
            btnToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            tvTimer.text = "00:00:00"; tvTiempoVacio.text = "00:00:00"
            cargarDatosDesdeDb()
        }
    }

    private fun confirmarCierre() {
        AlertDialog.Builder(this).setTitle("Finalizar Jornada").setMessage("¿Estás seguro?")
            .setPositiveButton("Sí") { _, _ -> mostrarDialogoDinero() }
            .setNegativeButton("No", null).show()
    }

    private fun mostrarDialogoDinero() {
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(60, 40, 60, 10) }
        val inG = EditText(this).apply { hint = "Ganancia"; setText("0"); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setSelectAllOnFocus(true); requestFocus() }
        val inP = EditText(this).apply { hint = "Propina"; setText("0"); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setSelectAllOnFocus(true) }
        layout.addView(inG); layout.addView(inP)
        val d = AlertDialog.Builder(this).setTitle("Resumen Sesión $jornadaActualId").setView(layout).setCancelable(false)
            .setPositiveButton("Guardar") { _, _ ->
                val g = inG.text.toString().toIntOrNull() ?: 0
                val p = inP.text.toString().toIntOrNull() ?: 0
                finalizarSesionDb(g, p)
                stopService(Intent(this, GpsService::class.java))
            }.setNegativeButton("Cancelar", null).create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE); d.show()
    }

    private fun finalizarSesionDb(g: Int, p: Int) {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val horaFin = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        lifecycleScope.launch { db.jornadaDao().finalizarSesionConDinero(fecha, jornadaActualId, g, p, horaFin) }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); drawable.setBounds(0, 0, canvas.width, canvas.height); drawable.draw(canvas)
        return bitmap
    }

    override fun onPause() { super.onPause(); map.onPause(); locationOverlay.disableMyLocation() }
}
