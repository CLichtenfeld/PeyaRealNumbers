package com.example.peyarealnumbers.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jornadas")
data class JornadaEntity(
    @PrimaryKey val fecha: String,
    val distanciaPlanaTotal: Double = 0.0,
    val distanciaRealTotal: Double = 0.0,
    val desnivelAcumulado: Double = 0.0,
    val gananciaTotal: Int = 0,
    val propinaTotal: Int = 0,
    val pedidosTotal: Int = 0,
    val tiempoTotalSegundos: Long = 0,
    val cantSesiones: Int = 0,
    val tiempoVacioTotalSeg: Long = 0,
    val joulesTotales: Double = 0.0,
    val joulesPlanoTotales: Double = 0.0,
    val esProcesada: Boolean = false // Indica si el día fue corregido topográficamente
)