package com.example.peyarealnumbers.utils

import android.content.Context
import android.util.Log
import com.example.peyarealnumbers.database.AppDatabase
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.File
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
            // 1. Extraer puntos del GPX (Lat/Lon)
            val puntos2D = extraerPuntosGpx(gpxFile)
            if (puntos2D.isEmpty()) return

            // 2. Pedir elevaciones reales a la API (en bloques de 100 para no saturar)
            val elevacionesReales = mutableListOf<ElevationResult>()
            puntos2D.chunked(100).forEach { chunk ->
                val resp = api.getElevations(ElevationRequest(chunk))
                elevacionesReales.addAll(resp.results)
            }

            // 3. Recalcular métricas físicas con altura real
            recalcularYActualizarDb(fecha, numeroSesion, elevacionesReales)

        } catch (e: Exception) {
            Log.e("Refinery", "Error refinando datos: ${e.message}")
        }
    }

    private fun extraerPuntosGpx(file: File): List<LocationPoint> {
        val puntos = mutableListOf<LocationPoint>()
        val content = file.readText()
        val pattern = Pattern.compile("lat=\"([^\"]+)\"\\s+lon=\"([^\"]+)\"")
        val matcher = pattern.matcher(content)
        while (matcher.find()) {
            puntos.add(LocationPoint(matcher.group(1).toDouble(), matcher.group(2).toDouble()))
        }
        return puntos
    }

    private suspend fun recalcularYActualizarDb(fecha: String, num: Int, puntos: List<ElevationResult>) {
        if (puntos.size < 2) return

        val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val masaTotal = (prefs.getFloat(AppConstants.KEY_PESO_RIDER, 80f) + prefs.getFloat(AppConstants.KEY_PESO_BICI, 15f)).toDouble()
        
        var d2dTotal = 0.0
        var d3dTotal = 0.0
        var desnivelPos = 0.0
        var joulesTotal = 0.0
        var joulesPlano = 0.0

        for (i in 0 until puntos.size - 1) {
            val p1 = puntos[i]
            val p2 = puntos[i+1]

            val d2d = haversine(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            val diffAlt = p2.elevation - p1.elevation
            
            d2dTotal += d2d
            if (diffAlt > 0) desnivelPos += diffAlt
            d3dTotal += sqrt(d2d.pow(2.0) + diffAlt.pow(2.0))

            // Estimación de velocidad promedio en el tramo (asumiendo intervalo GPS de 4s)
            val v = d2d / 4.0 
            val pendiente = if (d2d > 0) diffAlt / d2d else 0.0
            
            joulesTotal += calcularWatts(masaTotal, v, pendiente) * 4.0
            joulesPlano += calcularWatts(masaTotal, v, 0.0) * 4.0
        }

        // 4. Guardar en DB y marcar como procesada
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

    private fun calcularWatts(m: Double, v: Double, p: Double): Double {
        if (v < 0.5) return 0.0
        val pGravedad = m * 9.81 * v * p
        val pRodadura = m * 9.81 * v * 0.015
        val pAire = 0.5 * 1.225 * 0.5 * v.pow(3.0)
        val total = pGravedad + pRodadura + pAire
        return if (total < 0) 0.0 else total
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
