package com.example.peyarealnumbers.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker encargado de ejecutar la refinería topográfica en segundo plano.
 * Garantiza que el proceso se complete incluso si el usuario cierra la app.
 */
class ElevationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fecha = inputData.getString("fecha") ?: return Result.failure()
        val sesionId = inputData.getInt("sesionId", -1)
        if (sesionId == -1) return Result.failure()

        return try {
            Log.d("ElevationWorker", "Iniciando refinamiento para sesión $sesionId del día $fecha")
            val refinery = ElevationRefinery(applicationContext)
            refinery.refineSession(fecha, sesionId)
            Result.success()
        } catch (e: Exception) {
            Log.e("ElevationWorker", "Error en refinamiento: ${e.message}")
            Result.retry()
        }
    }
}
