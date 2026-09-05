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
