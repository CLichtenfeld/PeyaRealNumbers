package com.example.peyarealnumbers.utils

import java.util.Locale

object FormatUtils {
    fun formatElapsedTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    fun formatDistance(meters: Double): String {
        return if (meters >= 1000) {
            String.format(Locale.getDefault(), "%.2f km", meters / 1000.0)
        } else {
            String.format(Locale.getDefault(), "%d m", meters.toInt())
        }
    }
    
    fun formatKmh(speedKmh: Double): String {
        return String.format(Locale.getDefault(), "%.1f", speedKmh)
    }
}
