package com.example.peyarealnumbers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST = 1001
    private var corriendo = false

    private lateinit var receptorTimer: BroadcastReceiver
    private lateinit var btn: Button
    private lateinit var tvTimer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pedirPermisos()

        btn = findViewById(R.id.btnToggle)
        tvTimer = findViewById(R.id.tvTimer)

        btn.setOnClickListener {
            if (!corriendo) {
                startForegroundService(Intent(this, GpsService::class.java))
                btn.text = "DETENER RUTA"
                btn.setBackgroundColor(getColor(android.R.color.holo_red_light))
                corriendo = true
            } else {
                mostrarDialogoGanancia()
            }
        }



        receptorTimer = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val tiempo = intent.getStringExtra("tiempo") ?: "--:--:--"
                val jornada = intent.getIntExtra("jornada", 1)
                tvTimer.text = tiempo
                if (corriendo) btn.text = "DETENER — Jornada $jornada"
            }
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receptorTimer, IntentFilter("com.example.peyarealnumbers.TIMER"))
    }

    private fun mostrarDialogoGanancia() {
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val inputGanancia = android.widget.EditText(this)
        inputGanancia.hint = "Ganancia ($)"
        inputGanancia.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        val inputPropina = android.widget.EditText(this)
        inputPropina.hint = "Propina en efectivo ($)"
        inputPropina.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        layout.addView(inputGanancia)
        layout.addView(inputPropina)

        android.app.AlertDialog.Builder(this)
            .setTitle("¿Cómo te fue?")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val ganancia = inputGanancia.text.toString().toIntOrNull() ?: 0
                val propina = inputPropina.text.toString().toIntOrNull() ?: 0
                guardarSegmento(ganancia, propina)
                stopService(Intent(this, GpsService::class.java))
                btn.text = "INICIAR RUTA"
                btn.setBackgroundColor(getColor(android.R.color.holo_green_light))
                tvTimer.text = "--:--:--"
                corriendo = false
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarSegmento(ganancia: Int, propina: Int) {
        val fecha = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val hora = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val archivo = java.io.File(getExternalFilesDir(null), "jornada_$fecha.json")

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

            val segmentos = data.optJSONArray("segmentos") ?: org.json.JSONArray()
            val nuevo = org.json.JSONObject().apply {
                put("hora_fin", hora)
                put("ganancia", ganancia)
                put("propina", propina)
            }
            segmentos.put(nuevo)
            data.put("segmentos", segmentos)

            archivo.writeText(data.toString(2))
            android.util.Log.d("PeyaGPS", "Segmento guardado: $ganancia + $propina propina")

        } catch (e: Exception) {
            android.util.Log.e("PeyaGPS", "Error guardando segmento: ${e.message}")
        }
    }

    private fun pedirPermisos() {
        val permisos = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val falta = permisos.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (falta) ActivityCompat.requestPermissions(this, permisos, PERMISSION_REQUEST)
    }


    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receptorTimer)
    }
}