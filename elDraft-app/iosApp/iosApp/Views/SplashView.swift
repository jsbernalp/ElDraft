import SwiftUI
import shared

/// Espejo de `SplashScreen.kt`: logo, wordmark "elDraft" y tagline sobre
/// fondo a sangre completa, mientras se decide login-vs-home según
/// `AuthRepository.hasSession()`.
struct SplashView: View {
    /// `true` si hay sesión (ir a Home), `false` si no (ir a Login).
    let onResolved: (Bool) -> Void

    var body: some View {
        ZStack {
            ElDraftColors.splashBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                Image("SplashLogo")
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 140, height: 140)
                    .clipShape(Circle())

                Spacer().frame(height: ElDraftSpacing.lg2)

                // Wordmark "elDraft": "el" en acero, "Draft" en naranja de marca.
                HStack(spacing: 0) {
                    Text("el")
                        .font(ElDraftTextStyles.wordmark)
                        .tracking(ElDraftTextStyles.wordmarkTracking)
                        .foregroundStyle(ElDraftColors.splashSteel)
                    Text("Draft")
                        .font(ElDraftTextStyles.wordmark)
                        .tracking(ElDraftTextStyles.wordmarkTracking)
                        .foregroundStyle(ElDraftColors.primary)
                }

                Spacer().frame(height: ElDraftSpacing.xs)

                Text("ENCUENTRA TU PARTIDO")
                    .font(ElDraftTextStyles.tagline)
                    .tracking(ElDraftTextStyles.taglineTracking)
                    .foregroundStyle(ElDraftColors.splashTagline)
                    .multilineTextAlignment(.center)
            }
        }
        .task {
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            let hasSession = (try? await IosKoin.shared.authRepository.hasSession())?.boolValue ?? false
            onResolved(hasSession)
        }
    }
}
