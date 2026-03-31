package com.example.peyarealnumbers.utils

import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.database.JornadaEntity
import com.example.peyarealnumbers.database.SesionEntity
import java.text.SimpleDateFormat
import java.util.*

object MockDataUtils {

    suspend fun generarDatosDePrueba(db: AppDatabase, dias: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        // Generamos datos para la cantidad de días solicitada
        for (i in 0 until dias) {
            val fecha = sdf.format(calendar.time)
            
            // Si ya existen datos para este día, los saltamos para no duplicar
            if (db.jornadaDao().getJornadaPorFecha(fecha) != null) {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                continue
            }

            // Datos realistas solicitados:
            // Distancia: 30 +/- 10 km (Rango: 20 a 40 km)
            // Dinero: 50,000 +/- 15,000 (Rango: 35,000 a 65,000 pesos)
            
            val distanciaKm = (20..40).random().toDouble() + Math.random()
            val montoTotalAprox = (35000..65000).random()
            
            // Dividimos el monto entre ganancia base y propina (aprox 80/20)
            val gananciaBase = (montoTotalAprox * 0.8).toInt()
            val propinaBase = montoTotalAprox - gananciaBase
            
            val cantSesiones = (1..3).random()
            val pedidos = (10..25).random()
            val tiempoVacio = (1800..5400).random().toLong() // 30-90 min
            val joules = distanciaKm * 50000.0 // Estimación energética

            // 1. Insertar Jornada
            val jornada = JornadaEntity(
                fecha = fecha,
                distanciaPlanaTotal = distanciaKm * 0.95,
                distanciaRealTotal = distanciaKm,
                desnivelAcumulado = (100..500).random().toDouble(),
                gananciaTotal = gananciaBase,
                propinaTotal = propinaBase,
                pedidosTotal = pedidos,
                tiempoTotalSegundos = (6 * 3600).toLong(), // Aprox 6 horas
                cantSesiones = cantSesiones,
                tiempoVacioTotalSeg = tiempoVacio,
                joulesTotales = joules,
                joulesPlanoTotales = joules * 0.8,
                esProcesada = true
            )
            db.jornadaDao().insertarJornada(jornada)

            // 2. Insertar Sesiones para esa jornada
            for (s in 1..cantSesiones) {
                db.jornadaDao().insertarSesion(SesionEntity(
                    fechaPadre = fecha,
                    numeroSesion = s,
                    horaInicio = "10:00:00",
                    horaFin = "12:00:00",
                    distanciaPlanaM = (distanciaKm / cantSesiones) * 950,
                    distanciaRealM = (distanciaKm / cantSesiones) * 1000,
                    desnivelPositivoM = 100.0,
                    duracionSeg = (6 * 3600 / cantSesiones).toLong(),
                    ganancia = gananciaBase / cantSesiones,
                    propina = propinaBase / cantSesiones,
                    cantPedidos = pedidos / cantSesiones,
                    tiempoVacioSeg = tiempoVacio / cantSesiones,
                    joulesTotales = joules / cantSesiones,
                    joulesPlanoTotales = (joules * 0.8) / cantSesiones,
                    esProcesada = true
                ))
            }

            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
    }
}