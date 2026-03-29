package com.example.peyarealnumbers

class KalmanLatLong(private val qMetrosPorSegundo: Float) {
    private var variance = -1f
    private var lat = 0.0
    private var lng = 0.0

    fun filtrar(medicionLat: Double, medicionLng: Double, medicionAccuracy: Float, timestampMs: Long): DoubleArray {
        var accuracy = medicionAccuracy
        if (accuracy < 5f) accuracy = 5f

        if (variance < 0) {
            // Inicialización
            lat = medicionLat
            lng = medicionLng
            variance = accuracy * accuracy
        } else {
            // Predicción (basada en tiempo, simplificada)
            // Aquí asumimos un aumento de incertidumbre constante por segundo
            variance += qMetrosPorSegundo * qMetrosPorSegundo
            
            // Ganancia de Kalman
            val k = variance / (variance + accuracy * accuracy)
            
            // Actualización
            lat += k * (medicionLat - lat)
            lng += k * (medicionLng - lng)
            
            // Actualización de varianza
            variance *= (1 - k)
        }
        
        return doubleArrayOf(lat, lng)
    }
}