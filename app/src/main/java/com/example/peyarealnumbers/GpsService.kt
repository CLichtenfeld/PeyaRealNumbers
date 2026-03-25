package com.example.peyarealnumbers

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GpsService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var gpxFile: File
    private val channelId = "gps_channel"
    private var locationCallback: LocationCallback? = null
    private var ultimaPosicion: Location? = null
    private var tiempoQuieto: Long = 0
    private val UMBRAL_METROS = 30f
    private val UMBRAL_TIEMPO_MS = 3 * 60 * 1000L
    private var popupMostrado = false
    private var tiempoInicioSegmento: Long = 0
    private var numeroSegmento = 0
    private lateinit var timerHandler: android.os.Handler
    private lateinit var timerRunnable: Runnable

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("PeyaGPS", "onCreate")
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        crearNotificacion()
        iniciarTimer()
        iniciarSegmento()
        iniciarTracking()
    }

    private fun crearNotificacion() {
        val channel = NotificationChannel(channelId, "GPS Tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val intentAbrir = Intent(this, MainActivity::class.java)
        intentAbrir.action = Intent.ACTION_MAIN
        intentAbrir.addCategory(Intent.CATEGORY_LAUNCHER)
        intentAbrir.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val pendingAbrir = PendingIntent.getActivity(
            this, 0, intentAbrir,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Peya Real Numbers — Jornada 1")
            .setContentText("En curso: 00:00:00")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingAbrir)
            .build()
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun iniciarTimer() {
        timerHandler = android.os.Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                actualizarNotificacion()
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable)
    }

    private fun actualizarNotificacion() {
        val segundos = (System.currentTimeMillis() - tiempoInicioSegmento) / 1000
        val horas = segundos / 3600
        val mins = (segundos % 3600) / 60
        val segs = segundos % 60
        val tiempo = String.format("%02d:%02d:%02d", horas, mins, segs)

        val intentAbrir = Intent(this, MainActivity::class.java)
        intentAbrir.action = Intent.ACTION_MAIN
        intentAbrir.addCategory(Intent.CATEGORY_LAUNCHER)
        intentAbrir.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val pendingAbrir = PendingIntent.getActivity(
            this, 0, intentAbrir,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Peya Real Numbers — Jornada $numeroSegmento")
            .setContentText("En curso: $tiempo")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingAbrir)
            .build()
        getSystemService(NotificationManager::class.java).notify(1, notif)

        val intent = Intent("com.example.peyarealnumbers.TIMER")
        intent.putExtra("tiempo", tiempo)
        intent.putExtra("jornada", numeroSegmento)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun iniciarSegmento() {
        tiempoInicioSegmento = System.currentTimeMillis()
        numeroSegmento++
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        gpxFile = File(getExternalFilesDir(null), "jornada_$fecha.gpx")

        if (!gpxFile.exists()) {
            gpxFile.writeText("<?xml version=\"1.0\"?>\n<gpx version=\"1.1\">\n<trk>\n</trk>\n</gpx>")
            android.util.Log.d("PeyaGPS", "Archivo nuevo: ${gpxFile.name}")
        }

        val contenido = gpxFile.readText()
        val segmento = "<trkseg>\n</trkseg>\n</trk>"
        val nuevo = contenido.replace("</trk>", segmento)
        gpxFile.writeText(nuevo)
        android.util.Log.d("PeyaGPS", "Segmento iniciado")
    }

    private fun iniciarTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { guardarPunto(it) }
            }
        }
        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            android.util.Log.e("PeyaGPS", "Sin permiso GPS: ${e.message}")
        }
    }

    private fun guardarPunto(loc: Location) {
        val tiempo = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
        val alt = if (loc.hasAltitude()) loc.altitude else 0.0
        val punto = "<trkpt lat=\"${loc.latitude}\" lon=\"${loc.longitude}\"><ele>$alt</ele><time>$tiempo</time></trkpt>\n"

        val contenido = gpxFile.readText()
        val idx = contenido.lastIndexOf("</trkseg>")
        if (idx != -1) {
            val nuevo = contenido.substring(0, idx) + punto + contenido.substring(idx)
            gpxFile.writeText(nuevo)

            if (ultimaPosicion != null) {
                val distancia = loc.distanceTo(ultimaPosicion!!)
                if (distancia < UMBRAL_METROS) {
                    if (tiempoQuieto == 0L) tiempoQuieto = System.currentTimeMillis()
                    val quietoDesde = System.currentTimeMillis() - tiempoQuieto
                    if (quietoDesde >= UMBRAL_TIEMPO_MS && !popupMostrado) {
                        popupMostrado = true
                        enviarNotificacionInactividad()
                    }
                } else {
                    tiempoQuieto = 0L
                    popupMostrado = false
                }
            }
            ultimaPosicion = loc
        }
        android.util.Log.d("PeyaGPS", "Punto: ${loc.latitude}, ${loc.longitude}, alt: $alt")
    }

    private fun enviarNotificacionInactividad() {
        val intentEsperando = Intent(this, EstadoReceiver::class.java).apply {
            action = "com.example.peyarealnumbers.ESTADO"
            putExtra("tipo", "esperando")
        }
        val intentLibre = Intent(this, EstadoReceiver::class.java).apply {
            action = "com.example.peyarealnumbers.ESTADO"
            putExtra("tipo", "libre")
        }

        val pendingEsperando = PendingIntent.getBroadcast(
            this, 1, intentEsperando,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pendingLibre = PendingIntent.getBroadcast(
            this, 2, intentLibre,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intentPantalla = Intent(this, InactividadActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingPantalla = PendingIntent.getActivity(
            this, 3, intentPantalla,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Canal dedicado para inactividad con importancia alta
        val canalInactividad = "inactividad_channel"
        val canal = NotificationChannel(
            canalInactividad,
            "Inactividad",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)

        val notif = NotificationCompat.Builder(this, canalInactividad)
            .setContentTitle("¿Qué estás haciendo?")
            .setContentText("Llevas 3 min sin moverte")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingPantalla, true)
            .addAction(0, "Esperando pedido 🛵", pendingEsperando)
            .addAction(0, "Tiempo libre ☕", pendingLibre)
            .build()

        getSystemService(NotificationManager::class.java).notify(2, notif)

        timerHandler.postDelayed({ popupMostrado = false }, UMBRAL_TIEMPO_MS)
        android.util.Log.d("PeyaGPS", "Notificación inactividad enviada")
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        android.util.Log.d("PeyaGPS", "Jornada cerrada")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}