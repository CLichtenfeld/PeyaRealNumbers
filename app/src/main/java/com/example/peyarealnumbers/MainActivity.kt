package com.example.peyarealnumbers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration as AndroidConfig
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.utils.AppConstants
import com.example.peyarealnumbers.utils.ElevationWorker
import com.example.peyarealnumbers.utils.FormatUtils
import com.example.peyarealnumbers.utils.MockDataUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Pantalla principal de la aplicación.
 * Gestiona el tracking en tiempo real, visualización de mapa y resumen rápido de la jornada.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 1001
    private var jornadaActualId = 1

    // Componentes UI
    private lateinit var tvTimer: TextView
    private lateinit var tvJornadaStatus: TextView
    private lateinit var tvCurrentSpeed: TextView
    private lateinit var tvTotalPlana: TextView
    private lateinit var tvCurrentAlt: TextView
    private lateinit var tvTiempoVacio: TextView
    private lateinit var ivInfoKmPeya: ImageView
    private lateinit var ivInfoVacio: ImageView
    private lateinit var btnToggle: Button
    private lateinit var btnCenter: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnSupport: ImageButton
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

    // Inyección de Dependencias
    @Inject lateinit var db: AppDatabase

    private val handlerLongPress = Handler(Looper.getMainLooper())
    private var runnableSupport: Runnable? = null
    private var runnableTimer: Runnable? = null
    private var didLongPressSupport = false
    private var didLongPressTimer = false

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupOsmdroid()
        setContentView(R.layout.activity_main)

        vincularVistas()
        setupMapa()
        observarEstadoTracking()
        verificarPermisosYConfig()
        configurarListeners()
    }

    private fun setupOsmdroid() {
        Configuration.getInstance().userAgentValue = packageName
        @Suppress("DEPRECATION")
        Configuration.getInstance().load(this, android.preference.PreferenceManager.getDefaultSharedPreferences(this))
    }

    private fun verificarPermisosYConfig() {
        verificarPermisoSuperposicion()
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getFloat(AppConstants.KEY_PESO_RIDER, 0f) == 0f) {
            Toast.makeText(this, "Por favor, configura tu perfil para cálculos precisos", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ConfigRiderActivity::class.java))
        }
    }

    private fun configurarListeners() {
        btnToggle.setOnClickListener { gestionarClickToggle() }
        btnMenu.setOnClickListener { mostrarPopUpMenu() }
        btnSupport.setOnClickListener { startActivity(Intent(this, SoporteActivity::class.java)) }
        
        // Gesto de pulsación larga (3s) para utilidades de desarrollador/soporte
        btnSupport.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    didLongPressSupport = false
                    runnableSupport = Runnable {
                        didLongPressSupport = true
                        confirmarGenerarDatosPrueba()
                    }
                    handlerLongPress.postDelayed(runnableSupport!!, 3000)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handlerLongPress.removeCallbacks(runnableSupport ?: Runnable {})
                    if (event.action == MotionEvent.ACTION_UP && !didLongPressSupport) {
                        v.performClick()
                    }
                    runnableSupport = null
                }
            }
            true
        }

        // Timer Long Press para limpieza de datos locales
        tvTimer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    didLongPressTimer = false
                    runnableTimer = Runnable {
                        didLongPressTimer = true
                        confirmarBorradoHoy()
                    }
                    handlerLongPress.postDelayed(runnableTimer!!, 3000)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handlerLongPress.removeCallbacks(runnableTimer ?: Runnable {})
                    if (event.action == MotionEvent.ACTION_UP && !didLongPressTimer) {
                        v.performClick()
                    }
                    runnableTimer = null
                }
            }
            true
        }
        
        ivInfoKmPeya.setOnClickListener { mostrarDialogoInfo("Km Peya", "Distancia plana estándar. Útil para contrastar con los datos que reporta la app de pedidos.") }
        ivInfoVacio.setOnClickListener { mostrarDialogoInfo("Tiempo de Vacío", "Tiempo acumulado sin actividad productiva ni desplazamiento. No incluye esperas activas.") }
    }

    private fun confirmarGenerarDatosPrueba() {
        val opciones = arrayOf("1 Semana", "1 Mes", "1 Año")
        val diasOpciones = intArrayOf(7, 30, 365)

        AlertDialog.Builder(this)
            .setTitle("Generar Datos Simulados")
            .setItems(opciones) { _, which ->
                lifecycleScope.launch {
                    MockDataUtils.generarDatosDePrueba(db, diasOpciones[which])
                    Toast.makeText(this@MainActivity, "Simulación generada: ${opciones[which]}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarPopUpMenu() {
        val popup = PopupMenu(this, btnMenu)
        popup.menu.add("Historial de Rutas")
        popup.menu.add("Perfil del Rider")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Historial de Rutas" -> startActivity(Intent(this, HistorialActivity::class.java))
                "Perfil del Rider" -> startActivity(Intent(this, ConfigRiderActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun mostrarDialogoInfo(titulo: String, mensaje: String) {
        AlertDialog.Builder(this).setTitle(titulo).setMessage(mensaje).setPositiveButton("OK", null).show()
    }

    private fun gestionarClickToggle() {
        if (!TrackingManager.uiState.value.isTracking) {
            if (tengoPermisosUbicacion()) {
                startForegroundService(Intent(this, GpsService::class.java))
            } else {
                mostrarExplicacionPermisos()
            }
        } else {
            confirmarFinalizacionJornada()
        }
    }

    private fun verificarPermisoSuperposicion() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permiso de Superposición")
                .setMessage("Requerido para mostrar alertas de inactividad mientras usas otras aplicaciones de delivery.")
                .setPositiveButton("Configurar") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Después", null)
                .show()
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
        val background = if (Build.VERSION.SDK_INT >= 29) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine && background
    }

    private fun pedirPermisos() {
        val basicos = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) basicos.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 34) basicos.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        
        val faltanBasicos = basicos.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (faltanBasicos.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltanBasicos.toTypedArray(), permissionRequestCode)
        } else if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mostrarDialogoBackgroundLocation()
        }
    }

    private fun mostrarDialogoBackgroundLocation() {
        AlertDialog.Builder(this)
            .setTitle("Ubicación Todo el Tiempo")
            .setMessage("Para que el rastreo sea preciso con la pantalla apagada, selecciona 'Permitir todo el tiempo'.")
            .setPositiveButton("Ajustes") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), permissionRequestCode)
            }.setNegativeButton("Ignorar", null).show()
    }

    private fun mostrarExplicacionPermisos() {
        AlertDialog.Builder(this)
            .setTitle("Permisos Requeridos")
            .setMessage("Para auditar tus rutas necesitamos acceso permanente a la ubicación.")
            .setPositiveButton("Configurar") { _, _ -> pedirPermisos() }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun confirmarBorradoHoy() {
        AlertDialog.Builder(this)
            .setTitle("Limpiar Datos")
            .setMessage("¿Qué datos deseas purgar del almacenamiento local?")
            .setPositiveButton("Todo el Historial") { _, _ ->
                lifecycleScope.launch {
                    db.jornadaDao().eliminarTodoHistorial()
                    resetearInterfazUI()
                    Toast.makeText(this@MainActivity, "Base de datos reiniciada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Solo Hoy") { _, _ ->
                val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                lifecycleScope.launch {
                    db.jornadaDao().eliminarSesionesDelDia(fecha)
                    db.jornadaDao().eliminarJornada(fecha)
                    File(getExternalFilesDir(null), "jornada_$fecha.gpx").apply { if (exists()) delete() }
                    resetearInterfazUI()
                    Toast.makeText(this@MainActivity, "Datos de hoy eliminados", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun resetearInterfazUI() {
        tvTotalPlana.text = "0.00 km"
        tvTimer.text = "00:00:00"
        tvTiempoVacio.text = "00:00:00"
        actualizarBotonEstado(false)
    }

    private fun observarEstadoTracking() {
        lifecycleScope.launch {
            TrackingManager.uiState.collect { state ->
                tvTimer.text = state.tiempoTranscurrido
                tvTiempoVacio.text = state.tiempoVacioStr
                jornadaActualId = state.jornadaId
                tvCurrentSpeed.text = FormatUtils.formatKmh(state.speed)
                tvCurrentAlt.text = String.format(Locale.getDefault(), "%.0fm", state.altitude)
                
                actualizarBotonEstado(state.isTracking)
                
                if (state.isTracking) {
                    tvJornadaStatus.visibility = View.VISIBLE
                    cargarResumenDb()
                } else {
                    tvJornadaStatus.visibility = View.INVISIBLE
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
        tvTiempoVacio = findViewById(R.id.tvTiempoVacio)
        ivInfoKmPeya = findViewById(R.id.ivInfoKmPeya)
        ivInfoVacio = findViewById(R.id.ivInfoVacio)
        btnToggle = findViewById(R.id.btnToggle)
        btnCenter = findViewById(R.id.btnCenter)
        btnMenu = findViewById(R.id.btnMenu)
        btnSupport = findViewById(R.id.btnSupport)
    }

    private fun setupMapa() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        configurarIconoUbicacion()
        
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
        map.controller.setZoom(18.0)
        
        aplicarFiltroMapaNoche()
        btnCenter.setOnClickListener { locationOverlay.myLocation?.let { map.controller.animateTo(it) } }
    }

    private fun configurarIconoUbicacion() {
        ContextCompat.getDrawable(this, R.drawable.red_dot)?.let {
            val bitmap = if (it is BitmapDrawable) it.bitmap else {
                val b = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val c = Canvas(b); it.setBounds(0, 0, c.width, c.height); it.draw(c); b
            }
            locationOverlay.setPersonIcon(bitmap)
            locationOverlay.setDirectionIcon(bitmap)
            locationOverlay.setPersonAnchor(0.5f, 0.5f)
            locationOverlay.setDirectionAnchor(0.5f, 0.5f)
        }
    }

    private fun aplicarFiltroMapaNoche() {
        val isNight = (resources.configuration.uiMode and AndroidConfig.UI_MODE_NIGHT_MASK) == AndroidConfig.UI_MODE_NIGHT_YES
        if (isNight) {
            val matrix = ColorMatrix(floatArrayOf(
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            ))
            map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        }
    }

    private fun cargarResumenDb() {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch {
            db.jornadaDao().getJornadaPorFecha(fecha)?.let {
                tvTotalPlana.text = FormatUtils.formatDistance(it.distanciaPlanaTotal * 1000)
            }
        }
    }

    private fun actualizarBotonEstado(isCorriendo: Boolean) {
        if (isCorriendo) {
            btnToggle.setText(R.string.btn_finalizar_ruta)
            btnToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
        } else {
            btnToggle.setText(R.string.btn_iniciar_ruta)
            btnToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
        }
    }

    private fun confirmarFinalizacionJornada() {
        AlertDialog.Builder(this).setTitle("Cierre de Sesión").setMessage("¿Deseas finalizar el registro actual?")
            .setPositiveButton("Sí") { _, _ -> mostrarFormularioDinero() }
            .setNegativeButton("No", null).show()
    }

    private fun mostrarFormularioDinero() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_finalizar_sesion, null)
        val inG = view.findViewById<EditText>(R.id.etFinalizarGanancia)
        val inP = view.findViewById<EditText>(R.id.etFinalizarPropina)
        val inC = view.findViewById<EditText>(R.id.etFinalizarPedidos)
        
        AlertDialog.Builder(this).setView(view).setCancelable(false)
            .setPositiveButton("Guardar y Finalizar") { _, _ ->
                val g = inG.text.toString().toIntOrNull() ?: 0
                val p = inP.text.toString().toIntOrNull() ?: 0
                val c = inC.text.toString().toIntOrNull() ?: 0
                
                finalizarTracking(g, p, c)
            }
            .setNegativeButton("Regresar", null).show()
    }

    private fun finalizarTracking(g: Int, p: Int, c: Int) {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val horaFin = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        
        lifecycleScope.launch { 
            db.jornadaDao().finalizarSesionConDinero(fecha, jornadaActualId, g, p, c, horaFin)
            
            // Programamos el refinamiento topográfico mediante WorkManager (Senior Approach)
            lanzarRefineriaWorker(fecha, jornadaActualId)
            
            stopService(Intent(this@MainActivity, GpsService::class.java))
        }
    }

    private fun lanzarRefineriaWorker(fecha: String, sesionId: Int) {
        val data = workDataOf("fecha" to fecha, "sesionId" to sesionId)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<ElevationWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueue(request)
    }

    override fun onPause() { super.onPause(); map.onPause(); locationOverlay.disableMyLocation() }
}
