package com.example.peyarealnumbers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration as AndroidConfig
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.utils.AppConstants
import com.example.peyarealnumbers.utils.ElevationRefinery
import com.example.peyarealnumbers.utils.FormatUtils
import com.example.peyarealnumbers.utils.MockDataUtils
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
    private lateinit var tvTiempoVacio: TextView
    private lateinit var ivInfoKmPeya: ImageView
    private lateinit var ivInfoVacio: ImageView
    private lateinit var btnToggle: Button
    private lateinit var btnCenter: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnSupport: ImageButton
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var db: AppDatabase

    private val handlerLongPress = Handler(Looper.getMainLooper())
    private var runnableSupport: Runnable? = null
    private var runnableTimer: Runnable? = null
    private var didLongPressSupport = false
    private var didLongPressTimer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        configurarOsmdroid()
        setContentView(R.layout.activity_main)
        db = AppDatabase.getDatabase(this)

        vincularVistas()
        configurarMapa()
        observarEstadoTracking()
        verificarPermisoSuperposicion()
        verificarConfiguracionInicial()
        configurarListeners()
    }

    private fun configurarOsmdroid() {
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
    }

    private fun verificarConfiguracionInicial() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val pesoRider = prefs.getFloat(AppConstants.KEY_PESO_RIDER, 0f)
        if (pesoRider == 0f) {
            Toast.makeText(this, "Por favor, configura tu perfil para cálculos precisos", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ConfigRiderActivity::class.java))
        }
    }

    private fun configurarListeners() {
        btnToggle.setOnClickListener { gestionarClickToggle() }
        btnMenu.setOnClickListener { mostrarPopUpMenu() }
        btnSupport.setOnClickListener { startActivity(Intent(this, SoporteActivity::class.java)) }
        
        // MANTENER PRESIONADO 3 SEGUNDOS PARA GENERAR DATOS
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

        // MANTENER PRESIONADO EL TIMER 3 SEGUNDOS PARA BORRAR
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
        
        ivInfoKmPeya.setOnClickListener { mostrarDialogoInfo("Km Peya", "Distancia 'oficial' en plano. Sirve para comparar con lo que la app de pedidos suele marcar.") }
        ivInfoVacio.setOnClickListener { mostrarDialogoInfo("Tiempo de Vacío", "Tiempo acumulado 'al pedo' o muerto. No incluye el tiempo de espera activa por pedidos, sino el tiempo que realmente no estuviste produciendo ni moviéndote.") }
    }

    private fun confirmarGenerarDatosPrueba() {
        val opciones = arrayOf("1 Semana", "1 Mes", "1 Año")
        val diasOpciones = intArrayOf(7, 30, 365)

        AlertDialog.Builder(this)
            .setTitle("Generar datos de prueba")
            .setItems(opciones) { _, which ->
                val dias = diasOpciones[which]
                lifecycleScope.launch {
                    MockDataUtils.generarDatosDePrueba(db, dias)
                    Toast.makeText(this@MainActivity, "Datos de prueba generados (${opciones[which]})", Toast.LENGTH_SHORT).show()
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
        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun gestionarClickToggle() {
        if (!TrackingManager.uiState.value.isTracking) {
            if (tengoPermisosUbicacion()) {
                startForegroundService(Intent(this, GpsService::class.java))
            } else {
                mostrarExplicacionPermisos()
            }
        } else {
            confirmarCierre()
        }
    }

    private fun verificarPermisoSuperposicion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.alerta_inactividad_titulo))
                .setMessage("Para poder avisarte cuando llevas tiempo detenido, necesitamos el permiso de 'Mostrar sobre otras aplicaciones'.")
                .setPositiveButton("Ir a Ajustes") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
                }
                .setNegativeButton("Ahora no", null)
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
        val basicos = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 34) basicos.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        
        val faltanBasicos = basicos.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (faltanBasicos.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltanBasicos.toTypedArray(), PERMISSION_REQUEST)
        } else if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mostrarDialogoBackgroundLocation()
        }
    }

    private fun mostrarDialogoBackgroundLocation() {
        AlertDialog.Builder(this)
            .setTitle("Ubicación en segundo plano")
            .setMessage("Selecciona 'Permitir todo el tiempo' para que el rastreo no se detenga al apagar la pantalla.")
            .setPositiveButton("Configurar") { _, _ ->
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), PERMISSION_REQUEST)
            }.setNegativeButton("Después", null).show()
    }

    private fun mostrarExplicacionPermisos() {
        AlertDialog.Builder(this)
            .setTitle("Permisos de Ubicación")
            .setMessage("Para auditar tus rutas correctamente, necesitamos acceso 'Todo el tiempo'.")
            .setPositiveButton("Configurar") { _, _ -> pedirPermisos() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun confirmarBorradoHoy() {
        AlertDialog.Builder(this)
            .setTitle("Limpiar historial")
            .setMessage("¿Qué deseas borrar?")
            .setPositiveButton("Todo el historial") { _, _ ->
                lifecycleScope.launch {
                    db.jornadaDao().eliminarTodoHistorial()
                    resetearInterfaz()
                    Toast.makeText(this@MainActivity, "Todo el historial ha sido borrado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Solo hoy") { _, _ ->
                val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                lifecycleScope.launch {
                    db.jornadaDao().eliminarSesionesDelDia(fecha)
                    db.jornadaDao().eliminarJornada(fecha)
                    File(getExternalFilesDir(null), "jornada_$fecha.gpx").apply { if (exists()) delete() }
                    resetearInterfaz()
                    Toast.makeText(this@MainActivity, "Datos de hoy borrados", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun resetearInterfaz() {
        tvTotalPlana.text = "0.00 km"
        tvTimer.text = "00:00:00"
        tvTiempoVacio.text = "00:00:00"
        actualizarInterfaz(false)
    }

    private fun observarEstadoTracking() {
        lifecycleScope.launch {
            TrackingManager.uiState.collect { state ->
                actualizarUiDesdeEstado(state)
            }
        }
    }

    private fun actualizarUiDesdeEstado(state: TrackingManager.TrackingUiState) {
        tvTimer.text = state.tiempoTranscurrido
        tvTiempoVacio.text = state.tiempoVacioStr
        jornadaActualId = state.jornadaId
        tvCurrentSpeed.text = FormatUtils.formatKmh(state.speed)
        tvCurrentAlt.text = String.format("%.0fm", state.altitude)
        
        actualizarInterfaz(state.isTracking)
        
        if (state.isTracking) {
            tvJornadaStatus.visibility = android.view.View.VISIBLE
            cargarDatosDesdeDb()
        } else {
            tvJornadaStatus.visibility = android.view.View.INVISIBLE
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

    private fun configurarMapa() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        configurarIconoUbicacion()
        
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
        map.controller.setZoom(18.0)
        
        aplicarModoOscuroMapa()
        btnCenter.setOnClickListener { locationOverlay.myLocation?.let { map.controller.animateTo(it) } }
    }

    private fun configurarIconoUbicacion() {
        ContextCompat.getDrawable(this, R.drawable.red_dot)?.let {
            val bitmap = drawableToBitmap(it)
            locationOverlay.setPersonIcon(bitmap)
            locationOverlay.setDirectionIcon(bitmap)
            locationOverlay.setPersonAnchor(0.5f, 0.5f)
            locationOverlay.setDirectionAnchor(0.5f, 0.5f)
        }
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
                tvTotalPlana.text = FormatUtils.formatDistance(it.distanciaPlanaTotal * 1000)
            }
        }
    }

    private fun actualizarInterfaz(isCorriendo: Boolean) {
        if (isCorriendo) {
            btnToggle.text = getString(R.string.btn_finalizar_ruta)
            btnToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
        } else {
            btnToggle.text = getString(R.string.btn_iniciar_ruta)
            btnToggle.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
        }
    }

    private fun confirmarCierre() {
        AlertDialog.Builder(this).setTitle("Finalizar Jornada").setMessage("¿Estás seguro?")
            .setPositiveButton("Sí") { _, _ -> mostrarDialogoDinero() }
            .setNegativeButton("No", null).show()
    }

    private fun mostrarDialogoDinero() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_finalizar_sesion, null)
        val inG = view.findViewById<EditText>(R.id.etFinalizarGanancia)
        val inP = view.findViewById<EditText>(R.id.etFinalizarPropina)
        val inC = view.findViewById<EditText>(R.id.etFinalizarPedidos)
        
        AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Guardar") { _, _ ->
                val g = inG.text.toString().toIntOrNull() ?: 0
                val p = inP.text.toString().toIntOrNull() ?: 0
                val c = inC.text.toString().toIntOrNull() ?: 0
                finalizarSesionDb(g, p, c)
                stopService(Intent(this, GpsService::class.java))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun finalizarSesionDb(g: Int, p: Int, c: Int) {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val horaFin = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        lifecycleScope.launch { 
            db.jornadaDao().finalizarSesionConDinero(fecha, jornadaActualId, g, p, c, horaFin)
            ElevationRefinery(this@MainActivity).refineSession(fecha, jornadaActualId)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); drawable.setBounds(0, 0, canvas.width, canvas.height); drawable.draw(canvas)
        return bitmap
    }

    override fun onPause() { super.onPause(); map.onPause(); locationOverlay.disableMyLocation() }
}
