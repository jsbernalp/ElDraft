import SwiftUI
import shared

/// Cromo de un jugador ajeno, abierto al tocar un postulante en
/// `ApplicantsView`. Espejo de `PlayerCromoScreen.kt`.
struct PlayerCromoView: View {
    let playerId: String

    @State private var viewModel = PlayerCromoViewModel()

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                LoadingState()
            case .error(let message):
                Text(message)
                    .foregroundStyle(ElDraftColors.error)
                    .padding(ElDraftSpacing.xl)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .loaded(let profile):
                CromoContentView(profile: profile)
            }
        }
        .background(ElDraftColors.background)
        .task { viewModel.load(playerId: playerId) }
    }
}
