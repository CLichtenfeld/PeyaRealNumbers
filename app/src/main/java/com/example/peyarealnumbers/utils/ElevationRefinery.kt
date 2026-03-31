package com.example.peyarealnumbers.utils

import android.content.Context
import android.util.Log
import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.database.SesionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.*

// Modelos para la API de Open-Elevation
data class LocationPoint(val latitude: Double, val longitude: Double)
data class ElevationRequest(val locations: List<LocationPoint>)
data class ElevationResponse(val results: List<ElevationResult>)
data class ElevationResult(val latitude: Double, val longitude: Double, val elevation: Double)

interface ElevationApi {
    @POST("lookup")
    suspend fun getElevations(@Body request: ElevationRequest): ElevationResponse
}

/**
 * Clase encargada de refinar los datos de elevación y esfuerzo físico
 * utilizando datos topográficos reales de una API externa.
 */
class ElevationRefinery(val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    
    private val api = Retrofit.Builder()
        .baseUrl("https://api.open-elevation.com/api/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ElevationApi::class.java)

    suspend fun refineSession(fecha: String, numeroSesion: Int) {
        val gpxFile = File(context.getExternalFilesDir(null), "jornada_$fecha.gpx")
        if (!gpxFile.exists()) return

        try {
            // 1. Extraer puntos del GPX con sus respectivos Timestamps para velocidad real
            val puntosGpx = extraerPuntosGpxConTiempo(gpxFile)
            if (puntosGpx.isEmpty()) return

            val locationPoints = puntosGpx.map { LocationPoint(it.lat, it.lon) }

            // 2. Obtener elevaciones reales (por lotes para evitar límites de la API)
            val elevacionesReales = mutableListOf<ElevationResult>()
            locationPoints.chunked(100).forEach { chunk ->
                try {
                    val resp = api.getElevations(ElevationRequest(chunk))
                    elevacionesReales.addAll(resp.results)
                } catch (e: Exception) {
                    Log.e("Refinery", "Error en lote de elevación: ${e.message}")
                }
            }

            if (elevacionesReales.size == puntosGpx.size) {
                // 3. Recalcular métricas físicas con datos topográficos y tiempos reales
                recalcularMetricasSenior(fecha, numeroSesion, puntosGpx, elevacionesReales)
            }

        } catch (e: Exception) {
            Log.e("Refinery", "Error crítico en refinería: ${e.message}")
        }
    }

    private fun extraerPuntosGpxConTiempo(file: File): List<GpxPoint> {
        val puntos = mutableListOf<GpxPoint>()
        val content = file.readText()
        
        // Regex mejorado para capturar lat, lon y el tag <time> asociado
        val trkptPattern = Pattern.compile("<trkpt lat=\"([^\"]+)\" lon=\"([^\"]+)\">.*?<time>([^<]+)</time>", Pattern.DOTALL)
        val matcher = trkptPattern.matcher(content)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { 
            timeZone = TimeZone.getTimeZone("UTC") 
        }

        while (matcher.find()) {
            try {
                val lat = matcher.group(1)!!.toDouble()
                val lon = matcher.group(2)!!.toDouble()
                val timeStr = matcher.group(3)!!
                val time = sdf.parse(timeStr)?.time ?: 0L
                puntos.add(GpxPoint(lat, lon, time))
            } catch (e: Exception) {}
        }
        return puntos
    }

    private suspend fun recalcularMetricasSenior(fecha: String, num: Int, gpxPoints: List<GpxPoint>, altResult: List<ElevationResult>) {
        if (gpxPoints.size < 2) return

        val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val pesoRider = prefs.getFloat(AppConstants.KEY_PESO_RIDER, 80f).toDouble()
        val pesoBici = prefs.getFloat(AppConstants.KEY_PESO_BICI, 15f).toDouble()
        val masaTotal = pesoRider + pesoBici
        
        var d2dTotal = 0.0
        var d3dTotal = 0.0
        var desnivelPos = 0.0
        var joulesTotal = 0.0
        var joulesPlano = 0.0

        for (i in 0 until gpxPoints.size - 1) {
            val p1 = gpxPoints[i]
            val p2 = gpxPoints[i+1]
            val alt1 = altResult[i].elevation
            val alt2 = altResult[i+1].elevation

            val d2d = haversine(p1.lat, p1.lon, p2.lat, p2.lon)
            val diffAlt = alt2 - alt1
            val deltaTime = (p2.time - p1.time) / 1000.0 // en segundos

            d2dTotal += d2d
            if (diffAlt > 0) desnivelPos += diffAlt
            
            // Distancia real en 3D
            val d3d = sqrt(d2d.pow(2.0) + diffAlt.pow(2.0))
            d3dTotal += d3d

            if (deltaTime > 0 && d2d > 0.1) {
                val v = d2d / deltaTime // Velocidad real entre estos dos puntos
                val pendiente = diffAlt / d2d
                
                // Cálculo de energía consumida en este intervalo (Potencia * Tiempo)
                joulesTotal += calcularWattsSenior(masaTotal, v, pendiente) * deltaTime
                joulesPlano += calcularWattsSenior(masaTotal, v, 0.0) * deltaTime
            }
        }

        // Actualizar base de datos con los datos "Verificados"
        withContext(Dispatchers.IO) {
            val sesion = db.jornadaDao().getSesionEspecifica(fecha, num)
            sesion?.let {
                db.jornadaDao().insertarSesion(it.copy(
                    distanciaPlanaM = d2dTotal,
                    distanciaRealM = d3dTotal,
                    desnivelPositivoM = desnivelPos,
                    joulesTotales = joulesTotal,
                    joulesPlanoTotales = joulesPlano,
                    esProcesada = true
                ))
                db.jornadaDao().actualizarResumenDiario(fecha)
            }
        }
    }

    /**
     * Modelo físico avanzado de potencia.
     * Considera gravedad, rodadura y resistencia aerodinámica dinámica.
     */
    private fun calcularWattsSenior(m: Double, v: Double, pendiente: Double): Double {
        if (v < 0.5) return 0.0 // Ignorar ruido de estar detenido
        
        val g = 9.81
        val pGravedad = m * g * v * pendiente
        val pRodadura = m * g * v * 0.012 // Coeficiente para asfalto/ciudad
        val pAire = 0.5 * 1.225 * 0.6 * 0.5 * v.pow(3.0) // CdA estimado de un rider
        
        val potenciaMecanica = pGravedad + pRodadura + pAire
        
        // Si el resultado es negativo (bajada pronunciada), el rider no pedalea (0 Watts), 
        // pero la energía potencial se disipa en frenos.
        return if (potenciaMecanica < 0) 0.0 else potenciaMecanica
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    data class GpxPoint(val lat: Double, val lon: Double, val time: Long)
}
