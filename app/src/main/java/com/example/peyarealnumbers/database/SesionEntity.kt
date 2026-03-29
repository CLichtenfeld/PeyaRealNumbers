package com.example.peyarealnumbers.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "sesiones_db",
    foreignKeys = [
        ForeignKey(
            entity = JornadaEntity::class,
            parentColumns = ["fecha"],
            childColumns = ["fechaPadre"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SesionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fechaPadre: String, 
    val numeroSesion: Int,
    val nombrePersonalizado: String? = null,
    val horaInicio: String,
    val horaFin: String? = null,
    val distanciaPlanaM: Double = 0.0,
    val distanciaRealM: Double = 0.0,
    val desnivelPositivoM: Double = 0.0,
    val duracionSeg: Long = 0,
    val ganancia: Int = 0,
    val propina: Int = 0,
    val tiempoVacioSeg: Long = 0 // NUEVO CAMPO
)