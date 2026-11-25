import SwiftUI

/// Genera el QR de asistencia para mostrarlo en la cancha, con cuenta
/// regresiva y auto-regeneración al expirar. Espejo de `QRGeneratorScreen.kt`.
struct QRGeneratorView: View {
    let convocatoryId: String

    @State private var viewModel = AttendanceViewModel()

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            Spacer().frame(height: ElDraftSpacing.sm)
            ScreenHeader(
                title: String(localized: "qr_generator_title"),
                subtitle: String(localized: "qr_generator_subtitle"),
                alignment: .center
            )

            Spacer()

            ZStack {
                if let error = viewModel.qrState.error {
                    Text(error)
                        .font(ElDraftTypography.bodyMedium)
                        .foregroundStyle(ElDraftColors.error)
                        .multilineTextAlignment(.center)
                        .padding(ElDraftSpacing.lg)
                } else if let qr = viewModel.qrState.qrCode, let image = generateQrImage(qr) {
                    Image(uiImage: image)
                        .resizable()
                        .interpolation(.none)
                        .aspectRatio(contentMode: .fit)
                } else if viewModel.qrState.qrCode != nil {
                    Text(String(localized: "qr_generator_draw_error"))
                        .foregroundStyle(.black)
                } else {
                    ProgressView().tint(ElDraftColors.primary)
                }
            }
            .frame(width: 280, height: 280)
            .padding(ElDraftSpacing.lg)
            .background(Color.white, in: ElDraftShapes.md)

            Spacer().frame(height: ElDraftSpacing.xl)

            if viewModel.qrState.qrCode != nil {
                Text(String(format: String(localized: "qr_generator_expires_in"), formatMmSs(viewModel.qrState.secondsLeft)))
                    .font(ElDraftTypography.titleLarge)
                    .foregroundStyle(ElDraftColors.primary)
                Text(String(localized: "qr_generator_renews"))
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textMuted))
            }

            Spacer()
        }
        .padding(.horizontal, ElDraftSpacing.xl)
        .frame(maxWidth: .infinity)
        .background(ElDraftColors.background)
        .task(id: convocatoryId) { viewModel.loadQr(convocatoryId: convocatoryId) }
        .accessibilityIdentifier("qr_generator_view")
    }
}

private func formatMmSs(_ totalSeconds: Int64) -> String {
    let s = max(totalSeconds, 0)
    return String(format: "%02d:%02d", s / 60, s % 60)
}
