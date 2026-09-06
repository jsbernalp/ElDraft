import CoreImage.CIFilterBuiltins
import UIKit

/// Genera una `UIImage` con el código QR del `content` dado, usando
/// `CIQRCodeGenerator` (CoreImage) en vez de una librería externa como ZXing.
/// Devuelve `nil` si el contenido está vacío o la codificación falla. Espejo
/// de `generateQrBitmap` en `util/QrBitmap.kt`.
func generateQrImage(_ content: String, sizePoints: CGFloat = 280) -> UIImage? {
    guard !content.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
    guard let data = content.data(using: .utf8) else { return nil }

    let filter = CIFilter.qrCodeGenerator()
    filter.message = data
    // "M" ~15% de corrección — mismo nivel que `ErrorCorrectionLevel.M` en Android.
    filter.correctionLevel = "M"

    guard let output = filter.outputImage else { return nil }

    // El QR nativo sale minúsculo (una celda por punto); se escala sin
    // suavizado para que las celdas queden nítidas, no borrosas.
    let scale = sizePoints * UIScreen.main.scale / output.extent.width
    let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))

    let context = CIContext()
    guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
    return UIImage(cgImage: cgImage, scale: UIScreen.main.scale, orientation: .up)
}
