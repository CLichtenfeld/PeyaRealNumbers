package com.example.peyarealnumbers.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface JornadaDao {

    @Query("SELECT * FROM jornadas ORDER BY fecha DESC")
    fun getAllJornadas(): Flow<List<JornadaEntity>>

    @Query("SELECT * FROM jornadas WHERE fecha = :fecha LIMIT 1")
    suspend fun getJornadaPorFecha(fecha: String): JornadaEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarJornada(jornada: JornadaEntity)

    @Update
    suspend fun actualizarJornada(jornada: JornadaEntity)

    @Query("DELETE FROM jornadas WHERE fecha = :fecha")
    suspend fun eliminarJornada(fecha: String)

    @Query("DELETE FROM sesiones_db WHERE fechaPadre = :fecha")
    suspend fun eliminarSesionesDelDia(fecha: String)

    @Query("DELETE FROM jornadas")
    suspend fun eliminarTodoHistorial()

    @Query("DELETE FROM sesiones_db WHERE id = :sesionId")
    suspend fun eliminarSesionPorId(sesionId: Long)

    @Query("SELECT * FROM sesiones_db WHERE id = :sesionId LIMIT 1")
    suspend fun getSesionPorId(sesionId: Long): SesionEntity?

    @Transaction
    suspend fun eliminarSesionYRecalcular(sesionId: Long, fecha: String) {
        eliminarSesionPorId(sesionId)
        actualizarResumenDiario(fecha)
    }

    @Transaction
    suspend fun editarSesion(sesionId: Long, nuevaGanancia: Int, nuevaPropina: Int, nuevosPedidos: Int, nuevoNombre: String?, fecha: String) {
        val sesion = getSesionPorId(sesionId)
        sesion?.let {
            val actualizada = it.copy(ganancia = nuevaGanancia, propina = nuevaPropina, cantPedidos = nuevosPedidos, nombrePersonalizado = nuevoNombre)
            insertarSesion(actualizada)
            actualizarResumenDiario(fecha)
        }
    }

    @Query("SELECT * FROM sesiones_db WHERE fechaPadre = :fecha ORDER BY numeroSesion ASC")
    fun getSesionesDelDia(fecha: String): Flow<List<SesionEntity>>

    @Query("SELECT * FROM sesiones_db WHERE fechaPadre = :fecha AND numeroSesion = :num LIMIT 1")
    suspend fun getSesionEspecifica(fecha: String, num: Int): SesionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarSesion(sesion: SesionEntity): Long

    @Transaction
    suspend fun finalizarSesionConDinero(fecha: String, numSesion: Int, ganancia: Int, propina: Int, pedidos: Int, horaFin: String) {
        val sesion = getSesionEspecifica(fecha, numSesion)
        sesion?.let {
            val actualizada = it.copy(ganancia = ganancia, propina = propina, cantPedidos = pedidos, horaFin = horaFin)
            insertarSesion(actualizada)
            actualizarResumenDiario(fecha)
        }
    }

    @Transaction
    suspend fun actualizarResumenDiario(fecha: String) {
        val sesiones = getSesionesDirectas(fecha)
        if (sesiones.isEmpty()) {
            eliminarJornada(fecha)
            return
        }

        var dPlana = 0.0; var dReal = 0.0; var desnivel = 0.0
        var ganancia = 0; var propina = 0; var tiempo = 0L
        var tiempoVacio = 0L; var joules = 0.0; var joulesPlano = 0.0
        var pedidos = 0; var procesada = true

        for (s in sesiones) {
            dPlana += s.distanciaPlanaM
            dReal += s.distanciaRealM
            desnivel += s.desnivelPositivoM
            ganancia += s.ganancia
            propina += s.propina
            pedidos += s.cantPedidos
            tiempo += s.duracionSeg
            tiempoVacio += s.tiempoVacioSeg
            joules += s.joulesTotales
            joulesPlano += s.joulesPlanoTotales
            if (!s.esProcesada) procesada = false
        }

        val nuevaJornada = JornadaEntity(
            fecha = fecha,
            distanciaPlanaTotal = dPlana / 1000.0,
            distanciaRealTotal = dReal / 1000.0,
            desnivelAcumulado = desnivel,
            gananciaTotal = ganancia,
            propinaTotal = propina,
            pedidosTotal = pedidos,
            tiempoTotalSegundos = tiempo,
            cantSesiones = sesiones.size,
            tiempoVacioTotalSeg = tiempoVacio,
            joulesTotales = joules,
            joulesPlanoTotales = joulesPlano,
            esProcesada = procesada
        )
        
        insertarJornada(nuevaJornada)
        actualizarJornada(nuevaJornada)
    }

    @Query("SELECT * FROM sesiones_db WHERE fechaPadre = :fecha")
    suspend fun getSesionesDirectas(fecha: String): List<SesionEntity>
}