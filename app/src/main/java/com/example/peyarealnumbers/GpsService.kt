package com.example.peyarealnumbers

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

class GpsService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var gpxFile: File
    private val channelId = "gps_channel"
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("PeyaGPS", "onCreate")
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        crearNotificacion()
        iniciarSegmento()
        iniciarTracking()
    }

    private fun crearNotificacion() {
        val channel = NotificationChannel(channelId, "GPS Tracking", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Peya Real Numbers")
            .setContentText("Registrando ruta...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun iniciarSegmento() {
        val fecha = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        gpxFile = File(getExternalFilesDir(null), "ruta_$fecha.gpx")

        if (!gpxFile.exists()) {
            // Archivo nuevo
            gpxFile.writeText("<?xml version=\"1.0\"?>\n<gpx version=\"1.1\">\n<trk>\n</trk>\n</gpx>")
            android.util.Log.d("PeyaGPS", "Archivo nuevo: ${gpxFile.name}")
        }

        // Insertamos nuevo segmento antes de </trk>
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
        val punto = "<trkpt lat=\"${loc.latitude}\" lon=\"${loc.longitude}\" ele=\"$alt\"><time>$tiempo</time></trkpt>\n"

        val contenido = gpxFile.readText()
        val idx = contenido.lastIndexOf("</trkseg>")
        if (idx != -1) {
            val nuevo = contenido.substring(0, idx) + punto + contenido.substring(idx)
            gpxFile.writeText(nuevo)
        }
        android.util.Log.d("PeyaGPS", "Punto: ${loc.latitude}, ${loc.longitude}, alt: $alt")
    }

    override fun onDestroy() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        android.util.Log.d("PeyaGPS", "Segmento cerrado")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}