import AVFoundation
import SwiftUI

/// Cámara a pantalla completa que detecta códigos QR con
/// `AVCaptureMetadataOutput` (AVFoundation) — equivalente nativo de
/// CameraX + ML Kit en Android (`QrCodeAnalyzer.kt` + el `AndroidView` de
/// `QRScannerScreen.kt`). Llama `onQrDetected` con el primer valor
/// encontrado y deja de notificar tras el primero (one-shot), para evitar
/// múltiples envíos del mismo escaneo.
///
/// El simulador de iOS no tiene cámara: `AVCaptureDevice.default` devuelve
/// `nil` y la sesión nunca arranca, con lo que la vista simplemente se queda
/// en negro — mismo comportamiento que el `catch` de CameraX en Android
/// cuando el bind falla.
struct QrCodeScannerView: UIViewRepresentable {
    let onQrDetected: (String) -> Void

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        context.coordinator.configure(previewView: view)
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onQrDetected: onQrDetected)
    }

    static func dismantleUIView(_ uiView: PreviewView, coordinator: Coordinator) {
        coordinator.stop()
    }

    /// `UIView` cuya capa de respaldo ES la preview layer, para que el
    /// tamaño de la vista mueva la preview sin manejar frames a mano.
    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        private let onQrDetected: (String) -> Void
        private let session = AVCaptureSession()
        private var done = false

        init(onQrDetected: @escaping (String) -> Void) {
            self.onQrDetected = onQrDetected
        }

        func configure(previewView: PreviewView) {
            previewView.previewLayer.session = session
            previewView.previewLayer.videoGravity = .resizeAspectFill
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.setUpSession()
            }
        }

        private func setUpSession() {
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device) else { return }

            session.beginConfiguration()
            if session.canAddInput(input) { session.addInput(input) }

            let output = AVCaptureMetadataOutput()
            if session.canAddOutput(output) {
                session.addOutput(output)
                output.setMetadataObjectsDelegate(self, queue: .main)
                output.metadataObjectTypes = [.qr]
            }
            session.commitConfiguration()
            session.startRunning()
        }

        func metadataOutput(
            _ output: AVCaptureMetadataOutput,
            didOutput metadataObjects: [AVMetadataObject],
            from connection: AVCaptureConnection
        ) {
            guard !done,
                  let qr = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  qr.type == .qr,
                  let value = qr.stringValue, !value.isEmpty else { return }
            done = true
            onQrDetected(value)
        }

        func stop() {
            session.stopRunning()
        }
    }
}
