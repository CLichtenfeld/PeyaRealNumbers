package com.example.peyarealnumbers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SoporteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soporte)

        val btnBack = findViewById<ImageButton>(R.id.btnBackSoporte)
        val tvEmail = findViewById<TextView>(R.id.tvEmailSoporte)
        val tvWapp = findViewById<TextView>(R.id.tvWappSoporte)
        val etSugerencia = findViewById<EditText>(R.id.etSugerencia)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarSugerencia)

        btnBack.setOnClickListener { finish() }

        tvEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:lichchris@protonmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Consulta Peyaloche Rider")
            }
            try {
                startActivity(Intent.createChooser(intent, "Enviar correo"))
            } catch (e: Exception) {
                Toast.makeText(this, "No hay apps de correo instaladas", Toast.LENGTH_SHORT).show()
            }
        }

        tvWapp.setOnClickListener {
            val url = "https://api.whatsapp.com/send?phone=542944165330&text=Hola! Soy un Rider, tengo una consulta sobre la app Peyaloche"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }

        btnEnviar.setOnClickListener {
            val msj = etSugerencia.text.toString()
            if (msj.isNotBlank()) {
                // Simulamos el envío
                Toast.makeText(this, "¡Gracias! Tu sugerencia ha sido enviada", Toast.LENGTH_LONG).show()
                etSugerencia.text.clear()
            } else {
                Toast.makeText(this, "Por favor escribe algo antes de enviar", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
