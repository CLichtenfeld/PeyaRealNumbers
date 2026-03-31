package com.example.peyarealnumbers

class KalmanLatLong(private val qMetrosPorSegundo: Float) {
    private var variance = -1f
    private var lat = 0.0
    private var lng = 0.0
    private var lastTimestampMs = 0L

    fun reset() {
        variance = -1f
        lat = 0.0
        lng = 0.0
        lastTimestampMs = 0L
    }

    fun filtrar(medicionLat: Double, medicionLng: Double, medicionAccuracy: Float, timestampMs: Long): DoubleArray {
        var accuracy = medicionAccuracy
        if (accuracy < 5f) accuracy = 5f

        if (variance < 0) {
            // Inicialización con la primera medición
            lat = medicionLat
            lng = medicionLng
            variance = accuracy * accuracy
            lastTimestampMs = timestampMs
        } else {
            // Predicción basada en el tiempo transcurrido
            val deltaT = (timestampMs - lastTimestampMs).toFloat() / 1000f
            if (deltaT > 0) {
                // Aumentamos la incertidumbre proporcionalmente al tiempo y a la agilidad esperada (q)
                variance += deltaT * qMetrosPorSegundo * qMetrosPorSegundo
                lastTimestampMs = timestampMs
            }
            
            // Ganancia de Kalman: cuánto confiamos en la nueva medición vs el estado previo
            val k = variance / (variance + accuracy * accuracy)
            
            // Actualización del estado
            lat += k * (medicionLat - lat)
            lng += k * (medicionLng - lng)
            
            // Actualización de la varianza (reducimos incertidumbre tras la medición)
            variance *= (1 - k)
        }
        
        return doubleArrayOf(lat, lng)
    }
}