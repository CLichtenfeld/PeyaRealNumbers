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
    private lateinit var receptorInactividad: BroadcastReceiver
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

        receptorInactividad = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mostrarPopupInactividad()
            }
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receptorInactividad, IntentFilter("com.example.peyarealnumbers.INACTIVO"))

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

        val segmento = """{"hora_fin":"$hora","ganancia":$ganancia,"propina":$propina}"""

        if (archivo.exists()) {
            val contenido = archivo.readText()
            val nuevo = contenido.replace("]}", """,${segmento}]}""")
            archivo.writeText(nuevo)
        } else {
            archivo.writeText("""{"alias":"","segmentos":[${segmento}]}""")
        }
        android.util.Log.d("PeyaGPS", "Jornada guardada: $ganancia + $propina propina")
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

    private fun mostrarPopupInactividad() {
        android.app.AlertDialog.Builder(this)
            .setTitle("¿Qué estás haciendo?")
            .setItems(arrayOf("Esperando pedido 🛵", "Tiempo libre ☕")) { _, opcion ->
                val tipo = if (opcion == 0) "esperando" else "libre"
                android.util.Log.d("PeyaGPS", "Estado inactivo: $tipo")
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receptorInactividad)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receptorTimer)
    }
}