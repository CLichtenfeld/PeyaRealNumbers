package com.example.peyarealnumbers

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.location.Location
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
import com.example.peyarealnumbers.utils.AppConstants
import com.example.peyarealnumbers.utils.FormatUtils
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.*

@AndroidEntryPoint
class GpsService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var gpxFile: File
    private var locationCallback: LocationCallback? = null
    
    private var ultimaPosicion: Location? = null
    private var ultimaAlturaFiltrada: Double? = null
    private var distancia2D: Double = 0.0
    private var distancia3D: Double = 0.0
    private var desnivelPositivo: Double = 0.0
    private var joulesTotales: Double = 0.0
    private var joulesPlanoTotales: Double = 0.0
    
    private val filtroKalman = KalmanLatLong(3f)
    private var tiempoInicioSegmento: Long = 0
    private var numeroSegmento = 1
    private var sesionActualDbId: Long = -1L
    private var sessionReady = false
    
    @Inject lateinit var db: AppDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var jobTimer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var segundosDetenido = 0L
    private var segundosVacioTotal = 0L
    private var preguntandoInactividad = false
    private var registrandoVacioActual = false
    
    // Nueva variable para evitar bucles de alerta (cooldown de 10 min)
    private var ultimoTimestampRespuestaInactividad = 0L
    private val COOLDOWN_INACTIVIDAD_MS = 10 * 60 * 1000L 

    private var velocidadRealKmh = 0.0
    private var velocidadSuavizadaKmh = 0.0
    private var potenciaActualWatts = 0
    private var factorEsfuerzoActual = 100

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private var masaTotal = 105.0 
    private var crr = 0.015 
    private var cda = 0.5   
    private val GRAVEDAD = 9.81

    private val dateFormatIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val dateFormatDb = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormatDb = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val inactividadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val esVacio = intent?.getBooleanExtra(AppConstants.EXTRA_ES_VACIO, false) ?: false
            if (esVacio) {
                segundosVacioTotal += segundosDetenido
                registrandoVacioActual = true
            }
            preguntandoInactividad = false
            ultimoTimestampRespuestaInactividad = System.currentTimeMillis()
            quitarPestanaOverlay()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        cargarConfiguracionRider()
        inicializarComponentes()
        crearNotificacionForeground()
        
        serviceScope.launch {
            iniciarSegmentoSync()
            withContext(Dispatchers.Main) {
                iniciarTracking()
                iniciarTimer()
            }
        }
    }

    private fun cargarConfiguracionRider() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val pesoR = prefs.getFloat(AppConstants.KEY_PESO_RIDER, 80f)
        val pesoB = prefs.getFloat(AppConstants.KEY_PESO_BICI, 15f)
        val tipoV = prefs.getInt(AppConstants.KEY_TIPO_VEHICULO, 0)

        masaTotal = (pesoR + pesoB).toDouble()
        
        when (tipoV) {
            0 -> { crr = 0.015; cda = 0.5 }
            1 -> { crr = 0.018; cda = 0.6 }
            2 -> { crr = 0.020; cda = 0.8 }
        }
    }

    private fun inicializarComponentes() {
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Peyaloche::GpsWakeLock")
        if (wakeLock?.isHeld == false) wakeLock?.acquire()

        LocalBroadcastManager.getInstance(this).registerReceiver(inactividadReceiver, IntentFilter(AppConstants.ACTION_INACTIVIDAD))
        TrackingManager.setRunning(true)
    }

    private suspend fun iniciarSegmentoSync() {
        val hoy = Date()
        val fecha = dateFormatDb.format(hoy)
        val horaInicio = timeFormatDb.format(hoy)
        
        distancia2D = 0.0
        distancia3D = 0.0
        desnivelPositivo = 0.0
        joulesTotales = 0.0
        joulesPlanoTotales = 0.0
        segundosVacioTotal = 0L
        segundosDetenido = 0L
        ultimaPosicion = null
        ultimaAlturaFiltrada = null
        filtroKalman.reset()

        TrackingManager.updateMetrics(0.0, 0, 0.0, "0:00", 0, 0.0, 100)

        val ahora = System.currentTimeMillis()
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(AppConstants.KEY_INICIO_TIMESTAMP, ahora).apply()
        tiempoInicioSegmento = ahora
        
        if (db.jornadaDao().getJornadaPorFecha(fecha) == null) {
            db.jornadaDao().insertarJornada(JornadaEntity(fecha = fecha))
        }
        
        val sesionesExistentes = db.jornadaDao().getSesionesDirectas(fecha)
        numeroSegmento = sesionesExistentes.size + 1
        
        sesionActualDbId = db.jornadaDao().insertarSesion(SesionEntity(
            fechaPadre = fecha, 
            numeroSesion = numeroSegmento, 
            horaInicio = horaInicio
        ))
        
        sessionReady = true
        prepararArchivoGpx(fecha)
    }

    private fun prepararArchivoGpx(fecha: String) {
        gpxFile = File(getExternalFilesDir(null), "jornada_$fecha.gpx")
        if (gpxFile.exists()) quitarCierreXml(gpxFile)
        try {
            FileOutputStream(gpxFile, true).use { out ->
                if (gpxFile.length() < 10) {
                    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"PeyaRealNumbers\">\n<trk>\n".toByteArray())
                }
                out.write("<trkseg>\n".toByteArray())
            }
        } catch (e: Exception) { }
    }

    private fun guardarPunto(loc: Location) {
        if (!sessionReady) return
        
        if (ultimaPosicion == null && loc.accuracy > 15) return
        if (loc.accuracy > 25) return

        serviceScope.launch(Dispatchers.Default) {
            val puntoFiltrado = filtroKalman.filtrar(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis())
            val locFiltrada = Location(loc).apply { latitude = puntoFiltrado[0]; longitude = puntoFiltrado[1] }

            val speedMs = if (loc.hasSpeed()) {
                if (loc.speed < 0.6f) 0.0f else loc.speed
            } else {
                val old = ultimaPosicion
                if (old == null) 0.0f else {
                    val d = old.distanceTo(locFiltrada)
                    if (d > 50.0 && d < 1.0) 0.0f else (d / (AppConstants.GPS_INTERVAL_MS / 1000f)) 
                }
            }
            
            velocidadRealKmh = speedMs * 3.6
            actualizarMetricasFisicas(locFiltrada, speedMs.toDouble())
            escribirPuntoGpx(locFiltrada)
        }
    }

    private fun actualizarMetricasFisicas(locFiltrada: Location, velocidadMs: Double) {
        val old = ultimaPosicion
        if (old == null) {
            ultimaPosicion = locFiltrada
            ultimaAlturaFiltrada = if (locFiltrada.hasAltitude()) locFiltrada.altitude else null
            return
        }

        val d2d = locFiltrada.distanceTo(old).toDouble()
        val tiempoVivo = (System.currentTimeMillis() - tiempoInicioSegmento) / 1000

        if ((tiempoVivo < 5 && d2d > 10.0) || d2d > 100.0) {
            ultimaPosicion = locFiltrada
            ultimaAlturaFiltrada = if (locFiltrada.hasAltitude()) locFiltrada.altitude else null
            return
        }

        if (d2d > AppConstants.MIN_DISTANCE_UPDATE_METERS) {
            distancia2D += d2d
            
            val currentAlt = if (locFiltrada.hasAltitude()) locFiltrada.altitude else null
            val diffAlt = if (currentAlt != null && ultimaAlturaFiltrada != null) {
                currentAlt - ultimaAlturaFiltrada!!
            } else 0.0
            
            if (diffAlt > 0.5) desnivelPositivo += diffAlt
            distancia3D += sqrt(d2d.pow(2.0) + diffAlt.pow(2.0))

            val pendiente = if (d2d > 0) diffAlt / d2d else 0.0
            val wattsReal = calcularWatts(velocidadMs, pendiente)
            val wattsPlano = calcularWatts(velocidadMs, 0.0)
            
            potenciaActualWatts = wattsReal.toInt()
            factorEsfuerzoActual = if (wattsPlano > 10) ((wattsReal / wattsPlano) * 100).toInt() else 100
            
            val intervaloSeg = AppConstants.GPS_INTERVAL_MS / 1000.0
            joulesTotales += wattsReal * intervaloSeg
            joulesPlanoTotales += wattsPlano * intervaloSeg

            ultimaPosicion = locFiltrada
            ultimaAlturaFiltrada = currentAlt ?: ultimaAlturaFiltrada
        }
    }

    private fun calcularWatts(v: Double, p: Double): Double {
        if (v < 0.8) return 0.0
        val pGravedad = masaTotal * GRAVEDAD * v * p
        val pRodadura = masaTotal * GRAVEDAD * v * crr
        val pAire = 0.5 * 1.225 * cda * v.pow(3.0)
        val total = pGravedad + pRodadura + pAire
        return max(0.0, total)
    }

    private fun escribirPuntoGpx(loc: Location) {
        val lat = String.format(Locale.US, "%.7f", loc.latitude)
        val lon = String.format(Locale.US, "%.7f", loc.longitude)
        val tiempo = dateFormatIso.format(Date())
        val ele = if (loc.hasAltitude()) "<ele>${loc.altitude}</ele>" else ""
        val punto = "  <trkpt lat=\"$lat\" lon=\"$lon\">$ele<time>$tiempo</time></trkpt>\n"
        
        try {
            FileOutputStream(gpxFile, true).use { it.write(punto.toByteArray()) }
        } catch (e: Exception) { }
    }

    private fun mostrarPestanaOverlay() {
        if (overlayView != null) return
        
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
            
            overlayView?.findViewById<Button>(R.id.btnOverlayTrabajando)?.setOnClickListener { enviarBroadcastInactividad(false) }
            overlayView?.findViewById<Button>(R.id.btnOverlayVacio)?.setOnClickListener { enviarBroadcastInactividad(true) }

            serviceScope.launch(Dispatchers.Main) {
                try { 
                    windowManager.addView(overlayView, layoutParams)
                    reproducirSonidoAlerta()
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }

    private fun enviarBroadcastInactividad(esVacio: Boolean) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(AppConstants.ACTION_INACTIVIDAD).apply { putExtra(AppConstants.EXTRA_ES_VACIO, esVacio) }
        )
    }

    private fun reproducirSonidoAlerta() {
        try {
            // Sonido corto y discreto
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        } catch (e: Exception) { }
    }

    private fun quitarPestanaOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
    }

    private fun iniciarTimer() {
        var dbTickCounter = 0
        jobTimer = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val ahora = System.currentTimeMillis()
                val segTranscurridos = (ahora - tiempoInicioSegmento) / 1000
                
                TrackingManager.updateTimer(FormatUtils.formatElapsedTime(segTranscurridos), numeroSegmento)
                
                actualizarVelocidadSuavizada()
                gestionarInactividad()
                actualizarMetricasUI(segTranscurridos)

                dbTickCounter++
                if (dbTickCounter >= 10) {
                    actualizarDbMetricas()
                    dbTickCounter = 0
                }
                delay(1000)
            }
        }
    }

    private fun actualizarVelocidadSuavizada() {
        velocidadSuavizadaKmh += (velocidadRealKmh - velocidadSuavizadaKmh) * 0.4
        if (velocidadSuavizadaKmh < 0.5) velocidadSuavizadaKmh = 0.0
    }

    private fun gestionarInactividad() {
        if (velocidadSuavizadaKmh < 2.0) {
            segundosDetenido++
            
            // Lógica de Cooldown: Solo preguntar si pasaron 5 min desde el timeout Y 10 min desde la última respuesta
            val ahoraMs = System.currentTimeMillis()
            val enCooldown = (ahoraMs - ultimoTimestampRespuestaInactividad) < COOLDOWN_INACTIVIDAD_MS

            if (segundosDetenido >= AppConstants.INACTIVIDAD_TIMEOUT_SECONDS && 
                !preguntandoInactividad && !registrandoVacioActual && !enCooldown) {
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
    }

    private fun actualizarMetricasUI(segundosTotales: Long) {
        val d2d = distancia2D
        val d3d = distancia3D
        val diffPercent = if (d2d > 1.0) (((d3d - d2d) / d2d) * 100).toInt() else 0
        
        val paceStr = if (d3d > 10 && velocidadSuavizadaKmh > 2.0) {
            val paceMinPerKm = (segundosTotales / 60.0) / (d3d / 1000.0)
            val mins = paceMinPerKm.toInt()
            val secs = ((paceMinPerKm - mins) * 60).toInt()
            String.format(Locale.getDefault(), "%d:%02d", mins, secs)
        } else "0:00"

        val kcal = joulesTotales / 4184.0

        TrackingManager.updateMetrics(
            speed = velocidadSuavizadaKmh, 
            diffPercent = diffPercent, 
            altitude = ultimaPosicion?.altitude ?: 0.0, 
            pace = paceStr,
            powerWatts = potenciaActualWatts,
            totalKcal = kcal,
            effortFactor = factorEsfuerzoActual
        )
        TrackingManager.updateTiempoVacio(FormatUtils.formatElapsedTime(segundosVacioTotal))
    }

    private fun actualizarDbMetricas() {
        if (!sessionReady || sesionActualDbId == -1L) return
        val d2d = distancia2D
        val d3d = distancia3D
        val desnivel = desnivelPositivo
        val vacio = segundosVacioTotal
        val jt = joulesTotales
        val jpt = joulesPlanoTotales
        val segTrans = (System.currentTimeMillis() - tiempoInicioSegmento) / 1000
        val fecha = dateFormatDb.format(Date())

        serviceScope.launch {
            db.jornadaDao().getSesionPorId(sesionActualDbId)?.let {
                db.jornadaDao().insertarSesion(it.copy(
                    distanciaPlanaM = d2d, distanciaRealM = d3d,
                    desnivelPositivoM = desnivel, duracionSeg = segTrans,
                    tiempoVacioSeg = vacio,
                    joulesTotales = jt,
                    joulesPlanoTotales = jpt
                ))
                db.jornadaDao().actualizarResumenDiario(fecha)
            }
        }
    }

    private fun crearNotificacionForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(AppConstants.NOTIFICATION_CHANNEL_ID, "GPS Tracking", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        
        val notif = NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_content))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true).build()
        startForeground(AppConstants.NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun iniciarTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, AppConstants.GPS_INTERVAL_MS)
            .setMinUpdateIntervalMillis(AppConstants.GPS_FASTEST_INTERVAL_MS)
            .build()
        locationCallback = object : LocationCallback() { 
            override fun onLocationResult(result: LocationResult) { 
                result.lastLocation?.let { guardarPunto(it) } 
            } 
        }
        try { 
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper()) 
        } catch (e: SecurityException) { }
    }

    private fun quitarCierreXml(file: File) { 
        try { 
            val raf = RandomAccessFile(file, "rw")
            val len = raf.length()
            if (len > 30) raf.setLength(len - 20)
            raf.close() 
        } catch (e: Exception) { } 
    }

    override fun onDestroy() {
        jobTimer?.cancel()
        quitarPestanaOverlay()
        wakeLock?.let { if (it.isHeld) it.release() }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(inactividadReceiver)
        
        finalizarSesionYGuardar()
        
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(AppConstants.KEY_INICIO_TIMESTAMP).apply()
        TrackingManager.setRunning(false)
        
        try { 
            FileOutputStream(gpxFile, true).use { it.write("</trkseg>\n</trk>\n</gpx>".toByteArray()) } 
        } catch (e: Exception) { }
        
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        serviceScope.cancel()
        sesionActualDbId = -1L
        super.onDestroy()
    }

    private fun finalizarSesionYGuardar() {
        if (sesionActualDbId == -1L) return
        val fecha = dateFormatDb.format(Date())
        val d2d = distancia2D
        val d3d = distancia3D
        val desnivel = desnivelPositivo
        val segTrans = (System.currentTimeMillis() - tiempoInicioSegmento) / 1000
        val vacio = segundosVacioTotal
        val jt = joulesTotales
        val jpt = joulesPlanoTotales

        runBlocking {
            withTimeoutOrNull(2000) {
                db.jornadaDao().getSesionPorId(sesionActualDbId)?.let {
                    db.jornadaDao().insertarSesion(it.copy(
                        distanciaPlanaM = d2d, distanciaRealM = d3d,
                        desnivelPositivoM = desnivel, duracionSeg = segTrans,
                        tiempoVacioSeg = vacio,
                        joulesTotales = jt,
                        joulesPlanoTotales = jpt
                    ))
                    db.jornadaDao().actualizarResumenDiario(fecha)
                }
            }
        }
    }
}
