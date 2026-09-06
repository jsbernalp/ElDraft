import Foundation
import Observation
import shared

/// Estado del generador de QR (lado organizador o aprobado que lo muestra).
/// Espejo de `QrGenUiState` en `AttendanceViewModel.kt`.
struct QrGenUiState: Equatable {
    var qrCode: String?
    var secondsLeft: Int64 = 0
    var isLoading = false
    var error: String?
}

/// Estado del escáner (quien apunta la cámara). Espejo de `ScanUiState` en
/// `AttendanceViewModel.kt`.
enum ScanUiState: Equatable {
    case scanning
    case sending
    case success
    case error(message: String)
}

/// Genera el QR de asistencia (con cuenta regresiva y auto-regeneración al
/// expirar) y valida el QR escaneado. Espejo de `AttendanceViewModel.kt`. No
/// se cachea por convocatoryId como `NoShowViewModel`: cada pantalla (QR
/// generator/scanner) crea su propia instancia y vive solo mientras esa
/// pantalla está en el stack.
@MainActor
@Observable
final class AttendanceViewModel {
    private(set) var qrState = QrGenUiState()
    private(set) var scanState: ScanUiState = .scanning

    // Fuera de tracking de `@Observable`: no es estado de UI, solo el asa
    // para cancelar la cuenta regresiva anterior al pedir un QR nuevo o al
    // desaparecer la pantalla. `nonisolated(unsafe)` porque `deinit` (siempre
    // nonisolated) necesita cancelarla.
    @ObservationIgnored
    private nonisolated(unsafe) var qrTask: Task<Void, Never>?

    /// Genera el QR y arranca la cuenta regresiva; al expirar, lo regenera.
    func loadQr(convocatoryId: String) {
        qrTask?.cancel()
        qrState = QrGenUiState(isLoading: true)
        qrTask = Task {
            await runQrLoop(convocatoryId: convocatoryId)
        }
    }

    private func runQrLoop(convocatoryId: String) async {
        do {
            let qr = try await IosKoin.shared.generateAttendanceQr.invoke(convocatoryId: convocatoryId)
            qrState = QrGenUiState(qrCode: qr.qrCode, secondsLeft: qr.expiresInSeconds)
            var left = qr.expiresInSeconds
            while left > 0 {
                try Task.checkCancellation()
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                left -= 1
                qrState.secondsLeft = left
            }
            guard !Task.isCancelled else { return }
            // Expiró → regenerar automáticamente.
            await runQrLoop(convocatoryId: convocatoryId)
        } catch is CancellationError {
            // Pantalla cerrada o `loadQr` invocado de nuevo: no es un error.
        } catch {
            qrState = QrGenUiState(
                error: (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo generar el QR")
                    ?? "No se pudo generar el QR"
            )
        }
    }

    /// Envía el contenido del QR escaneado al backend. Idempotente por estado.
    func submitScan(qrCode: String) {
        guard scanState != .sending, scanState != .success else { return }
        scanState = .sending
        Task {
            do {
                _ = try await IosKoin.shared.scanAttendance.invoke(qrCode: qrCode)
                scanState = .success
            } catch {
                let message = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo validar el código")
                    ?? "No se pudo validar el código"
                scanState = .error(message: message)
            }
        }
    }

    /// Vuelve a permitir escanear tras un error.
    func resetScan() {
        scanState = .scanning
    }

    deinit {
        qrTask?.cancel()
    }
}
