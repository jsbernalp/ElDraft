import FirebaseAuth

/// Login/registro por email con Firebase Auth. Espejo de `EmailAuthClient`
/// (Android): devuelve el ID token de Firebase que el backend intercambia
/// por el JWT propio en `POST /auth/login` — el backend no distingue entre
/// login por email y por Google.
enum EmailAuthClientIOS {

    static func signIn(email: String, password: String) async throws -> String {
        let result = try await Auth.auth().signIn(withEmail: email, password: password)
        return try await result.user.getIDToken()
    }

    static func signUp(name: String, email: String, password: String) async throws -> String {
        let result = try await Auth.auth().createUser(withEmail: email, password: password)
        let changeRequest = result.user.createProfileChangeRequest()
        changeRequest.displayName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        try await changeRequest.commitChanges()
        return try await result.user.getIDToken()
    }
}
