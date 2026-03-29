package com.example.peyarealnumbers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.peyarealnumbers.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EstadoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val tipo = intent.getStringExtra("tipo") ?: return
        val jornadaId = intent.getIntExtra("jornada", 1)
        
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val hora = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        
        val db = AppDatabase.getDatabase(context)
        
        // Usamos una corrutina para guardar en DB fuera del hilo principal
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Por ahora, como las sesiones se guardan en el JSON/Entity, 
                // podríamos agregar una tabla de 'Eventos' si quisieras ver el detalle histórico.
                // De momento, guardamos el log para debug.
                android.util.Log.d("PeyaGPS", "Estado [$tipo] detectado a las $hora en jornada $jornadaId")
                
                // Cerrar notificación y avisar a la UI
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                nm.cancel(2)

                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent("com.example.peyarealnumbers.CERRAR_INACTIVIDAD"))
            } catch (e: Exception) {
                android.util.Log.e("PeyaGPS", "Error en Receiver: ${e.message}")
            }
        }
    }
}