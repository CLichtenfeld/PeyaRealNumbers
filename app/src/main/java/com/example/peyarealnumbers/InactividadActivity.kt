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
import com.example.peyarealnumbers.utils.AppConstants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InactividadActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        
        setContentView(R.layout.activity_inactividad)

        val btnVacio = findViewById<Button>(R.id.btnEsTiempoVacio)
        val btnLaburo = findViewById<Button>(R.id.btnEsEsperandoPedido)

        // Sonido discreto (Tipo Notificación)
        val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
        ringtone?.play()

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(500)
        }

        btnVacio.setOnClickListener {
            finalizarConAccion(true)
        }

        btnLaburo.setOnClickListener {
            finalizarConAccion(false)
        }
    }

    private fun finalizarConAccion(esVacio: Boolean) {
        detenerAlarma()
        val intent = Intent(AppConstants.ACTION_INACTIVIDAD).apply {
            putExtra(AppConstants.EXTRA_ES_VACIO, esVacio)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        finish()
    }

    private fun detenerAlarma() {
        try {
            ringtone?.let { if (it.isPlaying) it.stop() }
        } catch (e: Exception) {}
        ringtone = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            detenerAlarma()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        detenerAlarma()
        super.onDestroy()
    }
}
