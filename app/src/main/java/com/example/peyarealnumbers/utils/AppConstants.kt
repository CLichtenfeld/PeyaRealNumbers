package com.example.peyarealnumbers.utils

object AppConstants {
    // GPS Config
    const val GPS_INTERVAL_MS = 4000L
    const val GPS_FASTEST_INTERVAL_MS = 2000L
    const val MIN_DISTANCE_UPDATE_METERS = 3.0f
    
    // Inactividad
    const val INACTIVIDAD_TIMEOUT_SECONDS = 300L // 5 minutos
    
    // Notificaciones
    const val NOTIFICATION_CHANNEL_ID = "gps_tracking_channel"
    const val NOTIFICATION_ID = 1
    
    // SharedPreferences
    const val PREFS_NAME = "tracking_prefs"
    const val KEY_INICIO_TIMESTAMP = "inicio_timestamp"
    
    // Rider Config Keys
    const val KEY_PESO_RIDER = "peso_rider"
    const val KEY_PESO_BICI = "peso_bici"
    const val KEY_TIPO_VEHICULO = "tipo_vehiculo" // 0: Bici, 1: E-Bike, 2: Moto
    
    // BroadcastActions
    const val ACTION_INACTIVIDAD = "com.example.peyarealnumbers.ACCION_INACTIVIDAD"
    const val EXTRA_ES_VACIO = "es_vacio"
}
