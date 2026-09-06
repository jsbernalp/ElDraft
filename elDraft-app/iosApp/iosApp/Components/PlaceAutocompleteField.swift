import CoreLocation
import GooglePlaces
import SwiftUI

/// Una dirección elegida por el usuario en el buscador.
struct PlaceSelection {
    let description: String
    let coordinate: CLLocationCoordinate2D
}

/// Campo de búsqueda de direcciones con sugerencias en vivo (Places
/// Autocomplete). Al elegir una sugerencia, resuelve sus coordenadas y
/// notifica `onPlaceSelected`. Espejo de `PlaceAutocompleteField.kt`.
struct PlaceAutocompleteField: View {
    let onPlaceSelected: (PlaceSelection) -> Void

    @State private var query = ""
    @State private var predictions: [GMSAutocompletePlaceSuggestion] = []
    // Token de sesión: agrupa las búsquedas de una misma selección (facturación).
    @State private var sessionToken = GMSAutocompleteSessionToken()
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.xs) {
            HStack(spacing: ElDraftSpacing.sm) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.icon))
                TextField(String(localized: "place_search_label"), text: $query)
                    .onChange(of: query) { _, newValue in fetchPredictions(for: newValue) }
                if !query.isEmpty {
                    Button {
                        query = ""
                        predictions = []
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.icon))
                    }
                    .accessibilityLabel(String(localized: "place_clear_content_description"))
                }
            }
            .padding(.horizontal, ElDraftSpacing.md)
            .padding(.vertical, ElDraftSpacing.sm)
            .background(ElDraftColors.surface, in: ElDraftShapes.field)
            .overlay(ElDraftShapes.field.stroke(ElDraftColors.outline, lineWidth: ElDraftSizes.borderHairline))

            if let errorMessage {
                Text(errorMessage)
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.error)
            }

            if !predictions.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(predictions.prefix(5).enumerated()), id: \.offset) { index, prediction in
                        Button {
                            resolve(prediction)
                        } label: {
                            VStack(alignment: .leading, spacing: ElDraftSpacing.xxs) {
                                Text(prediction.attributedPrimaryText.string)
                                    .font(ElDraftTypography.bodyMedium)
                                    .fontWeight(.medium)
                                    .foregroundStyle(ElDraftColors.onSurface)
                                if let secondary = prediction.attributedSecondaryText?.string, !secondary.isEmpty {
                                    Text(secondary)
                                        .font(ElDraftTypography.bodySmall)
                                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, ElDraftSpacing.lg)
                            .padding(.vertical, ElDraftSpacing.md)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if index < min(predictions.count, 5) - 1 {
                            Divider().overlay(ElDraftColors.outline.opacity(ElDraftAlpha.containerStrong))
                        }
                    }
                }
                .background(ElDraftColors.surfaceVariant, in: ElDraftShapes.sm)
            }
        }
    }

    private func fetchPredictions(for text: String) {
        errorMessage = nil
        guard text.count >= 3 else {
            predictions = []
            return
        }
        let request = GMSAutocompleteRequest(query: text)
        request.sessionToken = sessionToken
        GMSPlacesClient.shared().fetchAutocompleteSuggestions(from: request) { suggestions, error in
            if let error {
                predictions = []
                errorMessage = String(format: String(localized: "place_suggestions_error"), String(error.localizedDescription.prefix(120)))
                return
            }
            // Cada sugerencia envuelve un `placeSuggestion` opcional; solo nos
            // interesan las que sí representan un lugar concreto.
            predictions = (suggestions ?? []).compactMap(\.placeSuggestion)
        }
    }

    private func resolve(_ prediction: GMSAutocompletePlaceSuggestion) {
        let fields = [GMSPlaceProperty.coordinate, GMSPlaceProperty.name, GMSPlaceProperty.formattedAddress].map(\.rawValue)
        let fetchRequest = GMSFetchPlaceRequest(placeID: prediction.placeID, placeProperties: fields, sessionToken: sessionToken)
        GMSPlacesClient.shared().fetchPlace(with: fetchRequest) { place, error in
            guard let place, error == nil else {
                errorMessage = String(localized: "place_resolve_error")
                return
            }
            onPlaceSelected(PlaceSelection(
                description: prediction.attributedFullText.string,
                coordinate: place.coordinate
            ))
            query = prediction.attributedPrimaryText.string
            predictions = []
            // Nuevo token para la siguiente búsqueda.
            sessionToken = GMSAutocompleteSessionToken()
        }
    }
}
