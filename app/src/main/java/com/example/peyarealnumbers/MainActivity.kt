package com.example.peyarealnumbers

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST = 1001
    private var corriendo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pedirPermisos()

        val btn = findViewById<Button>(R.id.btnToggle)
        btn.setOnClickListener {
            if (!corriendo) {
                startForegroundService(Intent(this, GpsService::class.java))
                btn.text = "DETENER RUTA"
                btn.setBackgroundColor(getColor(android.R.color.holo_red_light))
                corriendo = true
            } else {
                stopService(Intent(this, GpsService::class.java))
                btn.text = "INICIAR RUTA"
                btn.setBackgroundColor(getColor(android.R.color.holo_green_light))
                corriendo = false
            }
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
}