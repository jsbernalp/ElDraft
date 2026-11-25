import SwiftUI
import FirebaseCore
import GoogleMaps
import GooglePlaces
import GoogleSignIn
import UserNotifications
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

        // `provideAPIKey` hay que llamarlo SIEMPRE, incluso con
        // MAPS_API_KEY_IOS vacía: el SDK de Maps lanza `GMSServicesException`
        // al construir un `GMSMapView` si nunca se llamó, key vacía o no. Con
        // key vacía el mapa igual queda "muerto" (sin tiles), pero la app no
        // crashea al abrir la pestaña (ver Fase 3 del plan de port).
        GMSServices.provideAPIKey(AppConfig.mapsApiKey)
        GMSPlacesClient.provideAPIKey(AppConfig.mapsApiKey)

        KoinIosKt.startKoinIos(
            apiConfig: AppConfig.apiConfig,
            googleSignIn: LiveIdentity.googleSignInProvider(),
            emailAuth: LiveIdentity.emailAuthProvider(),
            identityCleaner: LiveIdentity.identitySessionCleaner()
        )

        // Fase 8 (parte sin cuenta Apple Developer de pago): categorías y
        // permiso de notificaciones locales listos ya. El push remoto (APNs)
        // sigue diferido — ver NotificationHelperIOS.
        UNUserNotificationCenter.current().delegate = PushNotificationDelegate.shared
        NotificationHelperIOS.ensureCategories()
        NotificationHelperIOS.requestAuthorization()
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
