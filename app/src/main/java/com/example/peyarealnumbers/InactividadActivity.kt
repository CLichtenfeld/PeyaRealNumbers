package com.example.peyarealnumbers

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.Intent

class InactividadActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Mostrar sobre pantalla de bloqueo y encender pantalla
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        
        setContentView(R.layout.activity_inactividad)

        val btnVacio = findViewById<Button>(R.id.btnEsTiempoVacio)
        val btnLaburo = findViewById<Button>(R.id.btnEsEsperandoPedido)

        // Hacer ruido y vibrar
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(applicationContext, notification)
        ringtone?.play()

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(1000)
        }

        btnVacio.setOnClickListener {
            detenerAlarma()
            val intent = Intent("ACCION_INACTIVIDAD").apply {
                putExtra("es_vacio", true)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            finish()
        }

        btnLaburo.setOnClickListener {
            detenerAlarma()
            val intent = Intent("ACCION_INACTIVIDAD").apply {
                putExtra("es_vacio", false)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            finish()
        }
    }

    private fun detenerAlarma() {
        ringtone?.let {
            if (it.isPlaying) {
                it.stop()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            detenerAlarma()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        detenerAlarma()
        super.onDestroy()
    }
}