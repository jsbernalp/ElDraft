package com.eldraft.android.ui.attendance

import com.eldraft.core.network.userMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.domain.usecase.attendance.GenerateAttendanceQrUseCase
import com.eldraft.domain.usecase.attendance.ScanAttendanceUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado del generador de QR (lado organizador). */
data class QrGenUiState(
    val qrCode: String? = null,
    val secondsLeft: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** Estado del escáner (lado jugador). */
sealed interface ScanUiState {
    data object Scanning : ScanUiState
    data object Sending : ScanUiState
    data object Success : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class AttendanceViewModel(
    private val generateQr: GenerateAttendanceQrUseCase,
    private val scanAttendance: ScanAttendanceUseCase,
) : ViewModel() {

    private val _qrState = MutableStateFlow(QrGenUiState())
    val qrState: StateFlow<QrGenUiState> = _qrState.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    /** Genera el QR y arranca la cuenta regresiva; al expirar, lo regenera. */
    fun loadQr(convocatoryId: String) {
        _qrState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val qr = generateQr(convocatoryId)
                _qrState.value = QrGenUiState(qrCode = qr.qrCode, secondsLeft = qr.expiresInSeconds)
                // Cuenta regresiva
                var left = qr.expiresInSeconds
                while (left > 0) {
                    delay(1000)
                    left -= 1
                    _qrState.update { it.copy(secondsLeft = left) }
                }
                // Expiró → regenerar automáticamente
                loadQr(convocatoryId)
            } catch (e: Exception) {
                _qrState.update {
                    it.copy(isLoading = false, error = e.userMessage("No se pudo generar el QR"))
                }
            }
        }
    }

    /** Envía el contenido del QR escaneado al backend. Idempotente por estado. */
    fun submitScan(qrCode: String) {
        if (_scanState.value is ScanUiState.Sending || _scanState.value is ScanUiState.Success) return
        _scanState.value = ScanUiState.Sending
        viewModelScope.launch {
            try {
                scanAttendance(qrCode)
                _scanState.value = ScanUiState.Success
            } catch (e: Exception) {
                _scanState.value = ScanUiState.Error(e.userMessage("No se pudo validar el código"))
            }
        }
    }

    /** Vuelve a permitir escanear tras un error. */
    fun resetScan() {
        _scanState.value = ScanUiState.Scanning
    }
}
