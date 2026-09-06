import AVFoundation
import SwiftUI

/// Cámara a pantalla completa con retícula animada para escanear el QR de
/// asistencia del OTRO rol (el organizador escanea a un convocado y
/// viceversa). Espejo de `QRScannerScreen.kt`.
///
/// El simulador de iOS no tiene cámara (ver `QrCodeScannerView`), así que en
/// builds `DEBUG` hay un atajo para pegar a mano el payload del QR y ejercitar
/// el mismo `ScanAttendanceUseCase` sin depender de hardware.
struct QRScannerView: View {
    let convocatoryId: String
    let isOrganizer: Bool
    let onScanComplete: () -> Void
    let onBack: () -> Void

    @State private var viewModel = AttendanceViewModel()
    @State private var hasCameraPermission = false
    #if DEBUG
    @State private var debugPayload = ""
    #endif

    private var otherRole: String {
        String(localized: isOrganizer ? "qr_scanner_role_player" : "qr_scanner_role_organizer")
    }

    private var reticleColor: Color {
        switch viewModel.scanState {
        case .success: return ElDraftColors.success
        case .error: return ElDraftColors.error
        default: return .white
        }
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if !hasCameraPermission {
                permissionMessage
            } else {
                QrCodeScannerView(onQrDetected: { qr in
                    Task { @MainActor in viewModel.submitScan(qrCode: qr) }
                })
                .ignoresSafeArea()
                ScannerOverlayView(reticleColor: reticleColor)
                    .ignoresSafeArea()
            }

            VStack {
                header
                Spacer()
                bottomContent
            }
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .task {
            hasCameraPermission = await requestCameraPermission()
        }
        .onChange(of: viewModel.scanState) { _, newValue in
            if newValue == .success {
                Task {
                    try? await Task.sleep(nanoseconds: 1_200_000_000)
                    onScanComplete()
                }
            }
        }
        .accessibilityIdentifier("qr_scanner_view")
    }

    private var permissionMessage: some View {
        VStack(spacing: ElDraftSpacing.lg) {
            Image(systemName: "qrcode.viewfinder")
                .font(.system(size: 56))
                .foregroundStyle(.white.opacity(ElDraftAlpha.textSecondary))
            Text(String(localized: "qr_scanner_permission_message"))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, ElDraftSpacing.xxl)
        }
    }

    private var header: some View {
        ZStack {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.title2)
                        .foregroundStyle(.white)
                }
                .padding(.leading, ElDraftSpacing.xs)
                Spacer()
            }
            Text(String(localized: "qr_scanner_scan_prompt"))
                .font(ElDraftTypography.titleLarge)
                .fontWeight(.bold)
                .foregroundStyle(.white)
        }
        .padding(.top, ElDraftSpacing.sm)
    }

    @ViewBuilder
    private var bottomContent: some View {
        VStack(spacing: ElDraftSpacing.lg) {
            switch viewModel.scanState {
            case .sending:
                StatusPillView(text: String(localized: "qr_scanner_validating"), showSpinner: true)
            case .success:
                StatusPillView(text: String(localized: "qr_scanner_success"), color: ElDraftColors.success)
            case .error(let message):
                StatusPillView(text: message, color: ElDraftColors.error)
                Button(String(localized: "qr_scanner_retry")) { viewModel.resetScan() }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.md)
                    .buttonStyle(.borderedProminent)
                    .tint(ElDraftColors.primary)
            case .scanning:
                ScanInstructionsView(otherRole: otherRole)
                #if DEBUG
                debugPasteField
                #endif
            }
        }
        .padding(.horizontal, ElDraftSpacing.lg2)
        .padding(.bottom, ElDraftSpacing.xl)
    }

    #if DEBUG
    /// Solo Debug: el simulador no tiene cámara (ver comentario de tipo en
    /// `QrCodeScannerView`), así que este campo pega a mano el payload del QR
    /// (mostrado por `QRGeneratorView` en otro dispositivo/cuenta) y llama al
    /// mismo `ScanAttendanceUseCase` que usaría la cámara real.
    private var debugPasteField: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.sm) {
            Text("DEBUG: pegar payload del QR")
                .font(ElDraftTypography.labelMedium)
                .foregroundStyle(.white.opacity(0.85)) // design-tokens-ignore: solo debug, sobre cámara
            HStack(spacing: ElDraftSpacing.sm) {
                TextField("qr_...", text: $debugPayload)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                Button("Enviar") {
                    let payload = debugPayload
                    debugPayload = ""
                    viewModel.submitScan(qrCode: payload)
                }
                .disabled(debugPayload.trimmingCharacters(in: .whitespaces).isEmpty)
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(ElDraftSpacing.md)
        .background(Color.black.opacity(ElDraftAlpha.icon), in: ElDraftShapes.md)
        .accessibilityIdentifier("qr_scanner_debug_paste_field")
    }
    #endif

    private func requestCameraPermission() async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return true
        case .notDetermined: return await AVCaptureDevice.requestAccess(for: .video)
        default: return false
        }
    }
}

/// Scrim oscuro a pantalla completa con una "ventana" central transparente y
/// un marco de esquinas que indica dónde apuntar el QR, más una línea de
/// escaneo animada. Espejo de `ScannerOverlay` en `QRScannerScreen.kt`.
private struct ScannerOverlayView: View {
    let reticleColor: Color

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { context, size in
                let side = min(size.width, size.height) * 0.68
                let left = (size.width - side) / 2
                let top = (size.height - side) / 2
                let corner: CGFloat = 28

                // Layer aislado: el `blendMode` de "recortar" la ventana solo
                // debe afectar al scrim de este Canvas, nunca a la cámara
                // detrás.
                context.drawLayer { layer in
                    layer.fill(
                        Path(CGRect(origin: .zero, size: size)),
                        with: .color(.black.opacity(ElDraftAlpha.scrim))
                    )
                    layer.blendMode = .destinationOut
                    layer.fill(
                        Path(roundedRect: CGRect(x: left, y: top, width: side, height: side), cornerRadius: corner),
                        with: .color(.black)
                    )
                }

                let len = side * 0.12
                let strokeWidth: CGFloat = 6
                func drawCorner(_ x: CGFloat, _ y: CGFloat, _ dx: CGFloat, _ dy: CGFloat) {
                    var horizontal = Path()
                    horizontal.move(to: CGPoint(x: x, y: y))
                    horizontal.addLine(to: CGPoint(x: x + len * dx, y: y))
                    context.stroke(horizontal, with: .color(reticleColor), style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round))

                    var vertical = Path()
                    vertical.move(to: CGPoint(x: x, y: y))
                    vertical.addLine(to: CGPoint(x: x, y: y + len * dy))
                    context.stroke(vertical, with: .color(reticleColor), style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round))
                }
                drawCorner(left, top, 1, 1)
                drawCorner(left + side, top, -1, 1)
                drawCorner(left, top + side, 1, -1)
                drawCorner(left + side, top + side, -1, -1)

                // Línea de escaneo: onda triangular con período de 3.6s,
                // equivalente al `tween(1800)` con `RepeatMode.Reverse` de Android.
                let t = timeline.date.timeIntervalSinceReferenceDate
                let phase = t.truncatingRemainder(dividingBy: 3.6) / 3.6
                let sweep = phase < 0.5 ? phase * 2 : (1 - phase) * 2
                let lineY = top + side * sweep
                var line = Path()
                line.move(to: CGPoint(x: left + len, y: lineY))
                line.addLine(to: CGPoint(x: left + side - len, y: lineY))
                context.stroke(
                    line,
                    with: .color(reticleColor.opacity(0.8)), // design-tokens-ignore: marco del escáner
                    style: StrokeStyle(lineWidth: 3, lineCap: .round)
                )
            }
        }
        .allowsHitTesting(false)
    }
}

/// Tarjeta de instrucciones paso a paso para el escaneo. Espejo de
/// `ScanInstructions` en `QRScannerScreen.kt`.
private struct ScanInstructionsView: View {
    let otherRole: String

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.lg) {
            Text(String(localized: "qr_scanner_help_title"))
                .font(ElDraftTypography.titleSmall)
                .fontWeight(.bold)
                .foregroundStyle(.white)
            StepLineView(number: "1", text: String(format: String(localized: "qr_scanner_step1"), otherRole))
            StepLineView(number: "2", text: String(localized: "qr_scanner_step2"))
        }
        .padding(.horizontal, ElDraftSpacing.lg2)
        .padding(.vertical, ElDraftSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.black.opacity(ElDraftAlpha.icon), in: ElDraftShapes.lg)
    }
}

private struct StepLineView: View {
    let number: String
    let text: String

    var body: some View {
        HStack(spacing: ElDraftSpacing.md) {
            Text(number)
                .font(ElDraftTypography.labelMedium)
                .fontWeight(.bold)
                .foregroundStyle(ElDraftColors.onPrimary)
                .frame(width: 26, height: 26)
                .background(ElDraftColors.primary, in: Circle())
            Text(text)
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(.white.opacity(0.92)) // design-tokens-ignore: tarjeta sobre cámara
        }
    }
}

private struct StatusPillView: View {
    let text: String
    var showSpinner = false
    var color: Color = ElDraftColors.primary

    var body: some View {
        HStack(spacing: ElDraftSpacing.md) {
            if showSpinner {
                ProgressView().tint(color)
            }
            Text(text)
                .font(ElDraftTypography.bodyLarge)
                .fontWeight(.semibold)
                .foregroundStyle(color)
        }
        .padding(.horizontal, ElDraftSpacing.lg2)
        .padding(.vertical, ElDraftSpacing.lg)
        .background(ElDraftColors.surface, in: ElDraftShapes.lg)
        .shadow(radius: ElDraftElevation.overlay)
    }
}
