package com.example.peyarealnumbers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.peyarealnumbers.database.AppDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class EstadoReceiver : BroadcastReceiver() {

    @Inject lateinit var db: AppDatabase

    override fun onReceive(context: Context, intent: Intent) {
        val tipo = intent.getStringExtra("tipo") ?: return
        val hora = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("PeyaGPS", "Estado [$tipo] detectado a las $hora")
                
                // Limpiar notificaciones de alerta
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(2)

                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent("com.example.peyarealnumbers.CERRAR_INACTIVIDAD"))
            } catch (e: Exception) {
                Log.e("PeyaGPS", "Error en Receiver: ${e.message}")
            }
        }
    }
}
