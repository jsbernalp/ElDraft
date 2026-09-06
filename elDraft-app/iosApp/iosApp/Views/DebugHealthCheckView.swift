import SwiftUI

/// Solo Debug: confirma que el simulador puede alcanzar el backend local
/// (`localhost` funciona en el simulador de iOS, a diferencia del `10.0.2.2`
/// del emulador Android) antes de construir nada sobre la red. Verificación
/// de la Fase 1 del port a iOS.
struct DebugHealthCheckView: View {
    @State private var responseText = "Toca el botón para probar la conexión."
    @State private var isLoading = false

    var body: some View {
        VStack(spacing: ElDraftSpacing.lg) {
            Text("GET \(AppConfig.apiBaseURL)/health")
                .font(ElDraftTypography.bodyLarge)
                .multilineTextAlignment(.center)

            Button(isLoading ? "Consultando…" : "Probar /health") {
                Task { await check() }
            }
            .disabled(isLoading)
            .buttonStyle(.borderedProminent)
            .tint(ElDraftColors.primary)

            ScrollView {
                Text(responseText)
                    .font(.system(.callout, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            // Fase 8 (sin cuenta Apple Developer de pago todavía): sin push
            // remoto no hay forma de disparar `NotificationHelperIOS.show`
            // desde un backend real. Este atajo la ejercita a mano —mismo
            // patrón que el pegado manual del payload de QR en
            // `QRScannerView` para sortear la falta de cámara del simulador—
            // para verificar categoría, sonido, deep link y refresco de una.
            Divider()
            Text("Notificaciones (Fase 8, sin push todavía)")
                .font(ElDraftTypography.labelLarge)
            VStack(spacing: ElDraftSpacing.sm) {
                Button("Simular: nueva convocatoria") {
                    NotificationHelperIOS.show(
                        title: "elDraft",
                        body: "Nuevo partido de Fútbol 5 cerca de ti",
                        type: "new_convocatory"
                    )
                }
                Button("Simular: nueva postulación") {
                    NotificationHelperIOS.show(
                        title: "elDraft",
                        body: "Alguien se postuló a tu convocatoria",
                        type: "new_postulation",
                        extraId: "debug-convocatory-id"
                    )
                }
                Button("Simular: postulación aprobada") {
                    NotificationHelperIOS.show(
                        title: "elDraft",
                        body: "Te aprobaron en el partido",
                        type: "postulation_approved"
                    )
                }
            }
            .buttonStyle(.bordered)
        }
        .padding(ElDraftSpacing.lg)
    }

    private func check() async {
        isLoading = true
        defer { isLoading = false }
        guard let url = URL(string: "\(AppConfig.apiBaseURL)/health") else {
            responseText = "URL inválida: \(AppConfig.apiBaseURL)/health"
            return
        }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            let status = (response as? HTTPURLResponse)?.statusCode ?? -1
            let body = String(data: data, encoding: .utf8) ?? "<sin cuerpo>"
            responseText = "HTTP \(status)\n\n\(body)"
        } catch {
            responseText = "Error: \(error.localizedDescription)"
        }
    }
}
