import SwiftUI
import shared

@main
struct ElDraftApp: App {

    init() {
        KoinIosKt.startKoinIos(
            apiConfig: AppConfig.apiConfig,
            googleSignIn: PlaceholderIdentity.googleSignInProvider(),
            emailAuth: PlaceholderIdentity.emailAuthProvider(),
            identityCleaner: PlaceholderIdentity.identitySessionCleaner()
        )
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
