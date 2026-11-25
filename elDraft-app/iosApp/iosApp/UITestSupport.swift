import Foundation

extension ProcessInfo {
    /// `true` cuando el proceso lo lanzó `iosAppUITests` (ver `AuthFlowUITests`,
    /// que pasa `-UITestMode` como launch argument). Único uso hoy: apagar el
    /// sheet de "guardar contraseña" del sistema en `ElDraftPasswordField`,
    /// que de otro modo interrumpe el tipeo automatizado a mitad de campo.
    static var isRunningUITests: Bool {
        CommandLine.arguments.contains("-UITestMode")
    }
}
