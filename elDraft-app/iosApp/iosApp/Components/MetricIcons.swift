import Foundation

/// Nombres SF Symbols canónicos de las métricas de reputación del jugador.
/// Punto único de verdad para toda pantalla que muestre habilidad/
/// deportividad/responsabilidad/asistencia, igual que `MetricIcons.kt` en
/// Android (íconos vectoriales, no emojis, para un lenguaje visual
/// consistente).
enum MetricIcons {
    static let skill = "soccerball"
    static let sportsmanship = "hand.raised.fill"
    static let responsibility = "checkmark.seal.fill"
    static let attendance = "calendar.badge.checkmark"
}
