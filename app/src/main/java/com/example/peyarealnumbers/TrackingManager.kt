package com.example.peyarealnumbers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TrackingManager {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    fun updateTimer(tiempo: String, jornadaId: Int) {
        _uiState.value = _uiState.value.copy(
            tiempoTranscurrido = tiempo,
            jornadaId = jornadaId
        )
    }

    fun updateMetrics(speed: Double, diffPercent: Int, altitude: Double, pace: String) {
        _uiState.value = _uiState.value.copy(
            speed = speed,
            diffPercent = diffPercent,
            altitude = altitude,
            pace = pace
        )
    }

    fun updateTiempoVacio(vacioStr: String) {
        _uiState.value = _uiState.value.copy(tiempoVacioStr = vacioStr)
    }

    fun setRunning(running: Boolean) {
        _uiState.value = _uiState.value.copy(isTracking = running)
        if (!running) {
            _uiState.value = _uiState.value.copy(
                tiempoTranscurrido = "00:00:00",
                tiempoVacioStr = "00:00:00",
                speed = 0.0,
                diffPercent = 0,
                altitude = 0.0,
                pace = "0:00"
            )
        }
    }

    data class TrackingUiState(
        val isTracking: Boolean = false,
        val tiempoTranscurrido: String = "00:00:00",
        val tiempoVacioStr: String = "00:00:00",
        val jornadaId: Int = 1,
        val speed: Double = 0.0,
        val diffPercent: Int = 0,
        val altitude: Double = 0.0,
        val pace: String = "0:00"
    )
}