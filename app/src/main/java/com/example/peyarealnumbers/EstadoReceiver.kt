package com.example.peyarealnumbers

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EstadoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val tipo = intent.getStringExtra("tipo") ?: return
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val archivo = File(context.getExternalFilesDir(null), "jornada_$fecha.json")

        try {
            val data = if (archivo.exists()) {
                org.json.JSONObject(archivo.readText())
            } else {
                org.json.JSONObject().apply {
                    put("alias", "")
                    put("segmentos", org.json.JSONArray())
                    put("estados", org.json.JSONArray())
                }
            }

            val estados = data.optJSONArray("estados") ?: org.json.JSONArray()
            val nuevoEstado = org.json.JSONObject().apply {
                put("hora", hora)
                put("tipo", tipo)
            }
            estados.put(nuevoEstado)
            data.put("estados", estados)

            archivo.writeText(data.toString(2))
            android.util.Log.d("PeyaGPS", "Estado guardado: $tipo a las $hora")

            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm.cancel(2)

            LocalBroadcastManager.getInstance(context)
                .sendBroadcast(Intent("com.example.peyarealnumbers.CERRAR_INACTIVIDAD"))

        } catch (e: Exception) {
            android.util.Log.e("PeyaGPS", "Error guardando estado: ${e.message}")
        }
    }
}