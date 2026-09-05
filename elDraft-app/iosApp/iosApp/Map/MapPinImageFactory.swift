import UIKit
import SwiftUI

/// Genera el ícono de marcador con forma de gota (círculo con borde blanco y
/// punta inferior) con un balón (convocatoria única) o un número (grupo) en
/// el centro. Espejo del `Canvas` de `MapPin()` en `MapTabContent.kt`
/// (Compose) traducido a `UIBezierPath`/`CoreGraphics`.
enum MapPinImageFactory {
    // Geometría de la gota, en puntos. Dimensiones de dibujo one-off, no
    // tokens — mismo caso que Android, que las deja literales (excepción de
    // CLAUDE.md: "dimensiones de dibujo en Canvas (pines del mapa)").
    private static let width: CGFloat = 44
    private static let height: CGFloat = 56
    private static let circleDiameter: CGFloat = 40
    private static let borderWidth: CGFloat = 3
    private static let innerDiscDiameter: CGFloat = 20

    /// `count == nil` dibuja el balón (convocatoria única); con `count`
    /// dibuja el número (varias convocatorias en la misma coordenada).
    static func pin(count: Int?) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        return renderer.image { _ in
            let rOuter = circleDiameter / 2
            let cx = width / 2
            let cy = rOuter + borderWidth / 2
            let tipY = height - 1
            let primary = UIColor(ElDraftColors.primary)

            // Sombra suave proyectada en el suelo, bajo la punta.
            let shadowRect = CGRect(x: cx - rOuter * 0.45, y: tipY - 2, width: rOuter * 0.9, height: rOuter * 0.5)
            UIColor.black.withAlphaComponent(0.15).setFill() // design-tokens-ignore: sombra del pin (dibujo)
            UIBezierPath(ovalIn: shadowRect).fill()

            UIColor.white.setFill()
            teardrop(cx: cx, cy: cy, radius: rOuter, tip: tipY).fill() // borde blanco
            primary.setFill()
            teardrop(cx: cx, cy: cy, radius: rOuter - borderWidth, tip: tipY - borderWidth * 1.4).fill() // relleno
            UIColor.white.setFill()
            let discRect = CGRect(x: cx - innerDiscDiameter / 2, y: cy - innerDiscDiameter / 2, width: innerDiscDiameter, height: innerDiscDiameter)
            UIBezierPath(ovalIn: discRect).fill() // disco central

            let contentCenter = CGPoint(x: cx, y: cy)
            if let count {
                drawCount(count, center: contentCenter, color: primary)
            } else {
                drawBall(center: contentCenter, color: primary)
            }
        }
    }

    /// Silueta de lágrima como un solo contorno continuo: arco superior del
    /// círculo + dos curvas que descienden tangentes hasta la punta.
    private static func teardrop(cx: CGFloat, cy: CGFloat, radius r: CGFloat, tip: CGFloat) -> UIBezierPath {
        let path = UIBezierPath()
        let a: CGFloat = 60 * .pi / 180
        let sx = r * sin(a)
        let sy = r * cos(a)
        let rightX = cx + sx
        let sideY = cy + sy

        path.move(to: CGPoint(x: cx - sx, y: sideY))
        // Lado izquierdo baja a la punta.
        path.addQuadCurve(to: CGPoint(x: cx, y: tip), controlPoint: CGPoint(x: cx - r * 0.32, y: cy + r * 1.7))
        // Lado derecho sube de la punta de vuelta al círculo.
        path.addQuadCurve(to: CGPoint(x: rightX, y: sideY), controlPoint: CGPoint(x: cx + r * 0.32, y: cy + r * 1.7))
        // Arco superior que cierra el círculo.
        path.addArc(withCenter: CGPoint(x: cx, y: cy), radius: r, startAngle: 120 * .pi / 180, endAngle: -180 * .pi / 180, clockwise: false)
        path.close()
        return path
    }

    private static func drawBall(center: CGPoint, color: UIColor) {
        let config = UIImage.SymbolConfiguration(pointSize: innerDiscDiameter * 0.8, weight: .regular)
        guard let ball = UIImage(systemName: "soccerball", withConfiguration: config)?
            .withTintColor(color, renderingMode: .alwaysOriginal) else { return }
        ball.draw(at: CGPoint(x: center.x - ball.size.width / 2, y: center.y - ball.size.height / 2))
    }

    private static func drawCount(_ count: Int, center: CGPoint, color: UIColor) {
        let text = "\(count)" as NSString
        let attrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 13, weight: .bold), // ligado al diámetro del disco, no un rol tipográfico
            .foregroundColor: color,
        ]
        let size = text.size(withAttributes: attrs)
        text.draw(at: CGPoint(x: center.x - size.width / 2, y: center.y - size.height / 2), withAttributes: attrs)
    }
}
