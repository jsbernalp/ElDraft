import SwiftUI
import FirebaseCore
import GoogleSignIn
import shared

@main
struct ElDraftApp: App {

    init() {
        FirebaseApp.configure()
        // El client id vive en GoogleService-Info.plist (leído por
        // FirebaseApp.configure()); GIDSignIn lo necesita aparte porque el SDK
        // de Google Sign-In es independiente del de Firebase.
        if let clientID = FirebaseApp.app()?.options.clientID {
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        }

        KoinIosKt.startKoinIos(
            apiConfig: AppConfig.apiConfig,
            googleSignIn: LiveIdentity.googleSignInProvider(),
            emailAuth: LiveIdentity.emailAuthProvider(),
            identityCleaner: LiveIdentity.identitySessionCleaner()
        )
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                // GIDSignIn abre Safari/una webview para el login y vuelve a la
                // app por este URL scheme (GOOGLE_REVERSED_CLIENT_ID); sin este
                // handler, "Continuar con Google" nunca completa el flujo.
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
