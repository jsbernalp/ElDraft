import SwiftUI

/// Avatar redondo con foto o inicial, compartido por las pantallas de equipos.
/// Espejo de `TeamPlayerAvatar` en `AddTeamPlayerSheet.kt`.
struct TeamPlayerAvatar: View {
    let name: String
    var avatarUrl: String? = nil

    private var initial: String {
        name.trimmingCharacters(in: .whitespaces).first.map(String.init)?.uppercased()
            ?? String(localized: "rating_initial_fallback")
    }

    var body: some View {
        ZStack {
            Circle().fill(ElDraftColors.primary.opacity(ElDraftAlpha.containerStrong))
            if let avatarUrl, !avatarUrl.isEmpty, let url = URL(string: avatarUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    ProgressView()
                }
                .frame(width: ElDraftSizes.avatar, height: ElDraftSizes.avatar)
                .clipShape(Circle())
                .accessibilityLabel(String(format: String(localized: "rating_avatar_content_description"), name))
            } else {
                Text(initial)
                    .font(ElDraftTypography.titleMedium)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.primary)
            }
        }
        .frame(width: ElDraftSizes.avatar, height: ElDraftSizes.avatar)
    }
}
