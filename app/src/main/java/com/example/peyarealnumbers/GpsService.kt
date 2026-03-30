package com.example.peyarealnumbers

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.location.Location
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.database.JornadaEntity
import com.example.peyarealnumbers.database.SesionEntity
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class GpsService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var gpxFile: File
    private val channelId = "gps_channel"
    private var locationCallback: LocationCallback? = null
    
    private var ultimaPosicion: Location? = null
    private var ultimaAlturaFiltrada: Double? = null
    private var distancia2D: Double = 0.0
    private var distancia3D: Double = 0.0
    private var desnivelPositivo: Double = 0.0
    
    private val filtroKalman = KalmanLatLong(3f)
    private var tiempoInicioSegmento: Long = 0
    private var numeroSegmento = 1
    private var sessionReady = false
    
    private lateinit var db: AppDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var jobTimer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var segundosDetenido = 0L
    private var segundosVacioTotal = 0L
    private var preguntandoInactividad = false
    private var registrandoVacioActual = false
    
    private var velocidadRealKmh = 0.0
    private var velocidadSuavizadaKmh = 0.0

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private val inactividadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val esVacio = intent?.getBooleanExtra("es_vacio", false) ?: false
            if (esVacio) {
                segundosVacioTotal += segundosDetenido
                registrandoVacioActual = true
            }
            preguntandoInactividad = false
            quitarPestanaOverlay()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("PeyaGps", "Servicio onCreate")
        
        segundosDetenido = 0L
        preguntandoInactividad = false
        registrandoVacioActual = false
        velocidadRealKmh = 0.0
        velocidadSuavizadaKmh = 0.0
        
        db = AppDatabase.getDatabase(this)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Peyaloche::GpsWakeLock")
        if (wakeLock?.isHeld == false) wakeLock?.acquire()

        LocalBroadcastManager.getInstance(this).registerReceiver(inactividadReceiver, IntentFilter("ACCION_INACTIVIDAD"))
        
        TrackingManager.setRunning(true)
        crearNotificacionForeground()
        
        serviceScope.launch {
            iniciarSegmentoSync()
            withContext(Dispatchers.Main) {
                iniciarTracking()
                iniciarTimer()
            }
        }
    }

    private suspend fun iniciarSegmentoSync() {
        val prefs = getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val horaInicio = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        
        tiempoInicioSegmento = if (TrackingManager.uiState.value.isTracking) {
             prefs.getLong("inicio_timestamp", System.currentTimeMillis())
        } else {
            val ahora = System.currentTimeMillis()
            prefs.edit().putLong("inicio_timestamp", ahora).apply()
            ahora
        }
        
        if (db.jornadaDao().getJornadaPorFecha(fecha) == null) {
            db.jornadaDao().insertarJornada(JornadaEntity(fecha = fecha))
        }
        
        val sesionesExistentes = db.jornadaDao().getSesionesDirectas(fecha)
        numeroSegmento = sesionesExistentes.size + 1
        
        db.jornadaDao().insertarSesion(SesionEntity(fechaPadre = fecha, numeroSesion = numeroSegmento, horaInicio = horaInicio))
        sessionReady = true

        gpxFile = File(getExternalFilesDir(null), "jornada_$fecha.gpx")
        if (gpxFile.exists()) quitarCierreXml(gpxFile)
        try {
            FileOutputStream(gpxFile, true).use { out ->
                if (gpxFile.length() < 10) out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\">\n<trk>\n".toByteArray())
                out.write("<trkseg>\n".toByteArray())
            }
        } catch (e: Exception) { }
    }

    private fun guardarPunto(loc: Location) {
        if (!sessionReady) return 
        if (loc.accuracy > 25) return

        val puntoFiltrado = filtroKalman.filtrar(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis())
        val locFiltrada = Location(loc).apply { latitude = puntoFiltrado[0]; longitude = puntoFiltrado[1] }

        val speedMs = if (loc.hasSpeed()) {
            if (loc.speed < 0.6f) 0.0f else loc.speed
        } else {
            val d = ultimaPosicion?.distanceTo(locFiltrada) ?: 0.0f
            // Ajustado a 4.0f para coincidir con el nuevo intervalo de GPS de 4 segundos
            if (d < 1.0f) 0.0f else (d / 4.0f) 
        }
        
        velocidadRealKmh = speedMs * 3.6

        ultimaPosicion?.let { old ->
            val d2d = locFiltrada.distanceTo(old).toDouble()
            if (d2d > 3.0) {
                distancia2D += d2d
                val diffAlt = if (loc.hasAltitude() && ultimaAlturaFiltrada != null) loc.altitude - ultimaAlturaFiltrada!! else 0.0
                if (diffAlt > 0.5) desnivelPositivo += diffAlt
                distancia3D += sqrt(d2d.pow(2.0) + diffAlt.pow(2.0))
                ultimaPosicion = locFiltrada
                ultimaAlturaFiltrada = loc.altitude
            }
        } ?: run {
            ultimaPosicion = locFiltrada
            ultimaAlturaFiltrada = loc.altitude
        }

        val lat = String.format(Locale.US, "%.7f", locFiltrada.latitude)
        val lon = String.format(Locale.US, "%.7f", locFiltrada.longitude)
        val tiempo = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val punto = "  <trkpt lat=\"$lat\" lon=\"$lon\">\n    <ele>${locFiltrada.altitude}</ele>\n    <time>$tiempo</time>\n  </trkpt>\n"
        try { FileOutputStream(gpxFile, true).use { it.write(punto.toByteArray()) } } catch (e: Exception) { }
    }

    private fun mostrarPestanaOverlay() {
        if (overlayView != null) return
        Log.d("PeyaGps", "Llamando a mostrarPestanaOverlay")
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            windowAnimations = android.R.style.Animation_InputMethod
        }

        try {
            val themedContext = ContextThemeWrapper(this, R.style.Theme_PeyaRealNumbers)
            overlayView = LayoutInflater.from(themedContext).inflate(R.layout.layout_inactividad_pestana, null)
            
            overlayView?.findViewById<Button>(R.id.btnOverlayTrabajando)?.setOnClickListener {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("ACCION_INACTIVIDAD").apply { putExtra("es_vacio", false) })
            }

            overlayView?.findViewById<Button>(R.id.btnOverlayVacio)?.setOnClickListener {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("ACCION_INACTIVIDAD").apply { putExtra("es_vacio", true) })
            }

            serviceScope.launch(Dispatchers.Main) {
                try { 
                    windowManager.addView(overlayView, layoutParams)
                    reproducirSonidoAlerta()
                    Log.d("PeyaGps", "Overlay añadido a WindowManager")
                } catch (e: Exception) {
                    Log.e("PeyaGps", "Error al añadir overlay: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("PeyaGps", "Error al inflar overlay: ${e.message}")
        }
    }

    private fun reproducirSonidoAlerta() {
        try {
            val alerta = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, alerta)
            r.play()
        } catch (e: Exception) {
            Log.e("PeyaGps", "No se pudo reproducir el sonido: ${e.message}")
        }
    }

    private fun quitarPestanaOverlay() {
        overlayView?.let {
            try { 
                windowManager.removeView(it)
                Log.d("PeyaGps", "Overlay removido")
            } catch (e: Exception) {}
            overlayView = null
        }
    }

    private fun iniciarTimer() {
        jobTimer = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val ahora = System.currentTimeMillis()
                val segundos = (ahora - tiempoInicioSegmento) / 1000
                TrackingManager.updateTimer(String.format(Locale.getDefault(), "%02d:%02d:%02d", segundos / 3600, (segundos % 3600) / 60, segundos % 60), numeroSegmento)
                
                // Factor de suavizado aumentado a 0.75 para reducir el retraso con el intervalo de 4s
                velocidadSuavizadaKmh += (velocidadRealKmh - velocidadSuavizadaKmh) * 0.75
                if (velocidadSuavizadaKmh < 0.5) velocidadSuavizadaKmh = 0.0

                if (velocidadSuavizadaKmh < 2.0) {
                    segundosDetenido++
                    
                    // Tiempo de inactividad restaurado a 5 minutos (300 segundos)
                    if (segundosDetenido >= 300L && !preguntandoInactividad && !registrandoVacioActual) {
                        preguntandoInactividad = true
                        mostrarPestanaOverlay()
                    }
                    if (registrandoVacioActual) segundosVacioTotal++
                } else {
                    segundosDetenido = 0
                    registrandoVacioActual = false
                    preguntandoInactividad = false
                    quitarPestanaOverlay()
                }

                ultimaPosicion?.let { 
                    actualizarMetricasUI(it, velocidadSuavizadaKmh) 
                }

                if (ahora % 10000 < 1000) actualizarDbMetricas()
                delay(1000)
            }
        }
    }

    private fun actualizarMetricasUI(loc: Location?, speedKmh: Double) {
        val diffPercent = if (distancia2D > 0) (((distancia3D - distancia2D) / distancia2D) * 100).toInt() else 0
        val segundosTotales = (System.currentTimeMillis() - tiempoInicioSegmento) / 1000
        val paceStr = if (distancia3D > 10 && speedKmh > 2.0) {
            val paceMinPerKm = (segundosTotales / 60.0) / (distancia3D / 1000.0)
            String.format(Locale.getDefault(), "%d:%02d", paceMinPerKm.toInt(), ((paceMinPerKm - paceMinPerKm.toInt()) * 60).toInt())
        } else "0:00"

        TrackingManager.updateMetrics(speed = speedKmh, diffPercent = diffPercent, altitude = loc?.altitude ?: 0.0, pace = paceStr)
        
        val h = segundosVacioTotal / 3600
        val m = (segundosVacioTotal % 3600) / 60
        val s = segundosVacioTotal % 60
        TrackingManager.updateTiempoVacio(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s))
    }

    private fun actualizarDbMetricas() {
        if (!sessionReady) return
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        serviceScope.launch {
            db.jornadaDao().getSesionEspecifica(fecha, numeroSegmento)?.let {
                db.jornadaDao().insertarSesion(it.copy(
                    distanciaPlanaM = distancia2D, distanciaRealM = distancia3D,
                    desnivelPositivoM = desnivelPositivo, duracionSeg = (System.currentTimeMillis() - tiempoInicioSegmento) / 1000,
                    tiempoVacioSeg = segundosVacioTotal
                ))
                db.jornadaDao().actualizarResumenDiario(fecha)
            }
        }
    }

    private fun crearNotificacionForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "GPS Tracking", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Peyaloche Rider").setContentText("Ruta activa...").setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun iniciarTracking() {
        // Intervalo de GPS ajustado a 4 segundos (4000 ms) por petición del usuario
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000).build()
        locationCallback = object : LocationCallback() { override fun onLocationResult(result: LocationResult) { result.lastLocation?.let { guardarPunto(it) } } }
        try { fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper()) } catch (e: SecurityException) { }
    }

    private fun quitarCierreXml(file: File) { try { val raf = RandomAccessFile(file, "rw"); val len = raf.length(); if (len > 30) raf.setLength(len - 20); raf.close() } catch (e: Exception) { } }

    override fun onDestroy() {
        jobTimer?.cancel()
        quitarPestanaOverlay()
        wakeLock?.let { if (it.isHeld) it.release() }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(inactividadReceiver)
        runBlocking {
            val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            db.jornadaDao().getSesionEspecifica(fecha, numeroSegmento)?.let {
                db.jornadaDao().insertarSesion(it.copy(
                    distanciaPlanaM = distancia2D, distanciaRealM = distancia3D,
                    desnivelPositivoM = desnivelPositivo, duracionSeg = (System.currentTimeMillis() - tiempoInicioSegmento) / 1000,
                    tiempoVacioSeg = segundosVacioTotal
                ))
                db.jornadaDao().actualizarResumenDiario(fecha)
            }
        }
        val prefs = getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("inicio_timestamp").apply()
        TrackingManager.setRunning(false)
        try { FileOutputStream(gpxFile, true).use { it.write("</trkseg>\n</trk>\n</gpx>".toByteArray()) } } catch (e: Exception) { }
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        serviceScope.cancel()
        super.onDestroy()
    }
}
