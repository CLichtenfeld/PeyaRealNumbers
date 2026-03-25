package com.example.peyarealnumbers

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InactividadActivity : AppCompatActivity() {

    private lateinit var receptorCerrar: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        receptorCerrar = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                cancelarNotificacion()
                finish()
            }
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receptorCerrar, IntentFilter("com.example.peyarealnumbers.CERRAR_INACTIVIDAD"))

        AlertDialog.Builder(this)
            .setTitle("¿Qué estás haciendo?")
            .setCancelable(false)
            .setItems(arrayOf("Esperando pedido 🛵", "Tiempo libre ☕")) { _, opcion ->
                val tipo = if (opcion == 0) "esperando" else "libre"
                guardarEstado(tipo)
                cancelarNotificacion()
                finish()
            }
            .show()
    }

    private fun cancelarNotificacion() {
        getSystemService(NotificationManager::class.java).cancel(2)
    }

    private fun guardarEstado(tipo: String) {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val archivo = File(getExternalFilesDir(null), "jornada_$fecha.json")

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

        } catch (e: Exception) {
            android.util.Log.e("PeyaGPS", "Error guardando estado: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receptorCerrar)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Bloquear botón atrás
    }
}