package com.example.peyarealnumbers

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.peyarealnumbers.utils.AppConstants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ConfigRiderActivity : AppCompatActivity() {

    private lateinit var etPesoRider: EditText
    private lateinit var etPesoBici: EditText
    private lateinit var rgTipoVehiculo: RadioGroup
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_rider)

        vincularVistas()
        cargarDatosGuardados()
        configurarListeners()
    }

    private fun vincularVistas() {
        etPesoRider = findViewById(R.id.etPesoRider)
        etPesoBici = findViewById(R.id.etPesoBici)
        rgTipoVehiculo = findViewById(R.id.rgTipoVehiculo)
        btnSave = findViewById(R.id.btnSaveConfig)
        btnBack = findViewById(R.id.btnBackConfig)
    }

    private fun cargarDatosGuardados() {
        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        
        val pesoRider = prefs.getFloat(AppConstants.KEY_PESO_RIDER, 80f)
        val pesoBici = prefs.getFloat(AppConstants.KEY_PESO_BICI, 15f)
        val tipoVehiculo = prefs.getInt(AppConstants.KEY_TIPO_VEHICULO, 0) // 0: Bici

        etPesoRider.setText(pesoRider.toInt().toString())
        etPesoBici.setText(pesoBici.toInt().toString())

        when (tipoVehiculo) {
            0 -> rgTipoVehiculo.check(R.id.rbBici)
            1 -> rgTipoVehiculo.check(R.id.rbEBike)
            2 -> rgTipoVehiculo.check(R.id.rbMoto)
        }
    }

    private fun configurarListeners() {
        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            guardarConfiguracion()
        }
    }

    private fun guardarConfiguracion() {
        val pesoR = etPesoRider.text.toString().toFloatOrNull() ?: 80f
        val pesoB = etPesoBici.text.toString().toFloatOrNull() ?: 15f
        
        val tipoVehiculo = when (rgTipoVehiculo.checkedRadioButtonId) {
            R.id.rbBici -> 0
            R.id.rbEBike -> 1
            R.id.rbMoto -> 2
            else -> 0
        }

        val prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat(AppConstants.KEY_PESO_RIDER, pesoR)
            putFloat(AppConstants.KEY_PESO_BICI, pesoB)
            putInt(AppConstants.KEY_TIPO_VEHICULO, tipoVehiculo)
            apply()
        }

        Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
        finish()
    }
}
