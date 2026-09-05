import XCTest

/// Cubre la verificación de la Fase 2 del plan de port a iOS: registro por
/// email, `needsOnboarding` lleva a la ficha técnica, guardarla lleva a
/// Home, la sesión persiste en Keychain tras cerrar/reabrir la app, y logout
/// vuelve a pedir login. Corre contra el backend local (docker compose +
/// `:backend:run`), igual que el resto de la verificación manual del plan.
///
/// A diferencia de `:shared:iosSimulatorArm64Test` (un binario nativo suelto
/// sin acceso a Keychain), este target corre dentro de una app instalada de
/// verdad en el simulador — es la única forma de probar persistencia real.
final class AuthFlowUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testRegistroOnboardingPersistenciaYLogout() throws {
        let app = XCUIApplication()
        // Apaga el sheet de "guardar contraseña" del sistema (ver
        // `ProcessInfo.isRunningUITests` / `ElDraftPasswordField`): sin esto,
        // interrumpe el tipeo automatizado a mitad de campo.
        app.launchArguments = ["-UITestMode"]
        app.launch()

        // El Keychain sobrevive a la reinstalación de la app entre corridas
        // (comportamiento correcto, no un bug): si una sesión previa sigue
        // viva, el splash cae directo en Home. Partimos de un estado limpio
        // con el logout de debug antes de registrar un usuario nuevo.
        if app.tabBars.buttons["Organizo"].waitForExistence(timeout: 3) {
            app.buttons["debug_logout_button"].tap()
            XCTAssertTrue(app.buttons["login_chip_signin"].waitForExistence(timeout: 5), "No se pudo limpiar la sesión previa antes de empezar.")
        }

        let uniqueEmail = "uitest.\(Int(Date().timeIntervalSince1970))@example.com"

        app.buttons["login_chip_signup"].tap()

        let nameField = app.textFields["login_name_field"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5))
        nameField.tap()
        nameField.typeText("UI Test")

        let emailField = app.textFields["login_email_field"]
        emailField.tap()
        emailField.typeText(uniqueEmail)

        let passwordField = app.secureTextFields["login_password_field"]
        passwordField.tap()
        passwordField.typeText("Test1234")

        let confirmField = app.secureTextFields["login_confirm_password_field"]
        confirmField.tap()
        confirmField.typeText("Test1234")

        app.buttons["login_submit_button"].tap()

        // needsOnboarding = true para un usuario nuevo: debe caer en la ficha técnica.
        let positionField = app.buttons["onboarding_position_primary_field"]
        XCTAssertTrue(positionField.waitForExistence(timeout: 10), "No llegó a onboarding tras registrarse.")

        positionField.tap()
        app.buttons["Delantero"].tap()

        app.buttons["onboarding_dominant_foot_field"].tap()
        app.buttons["Derecho"].tap()

        app.buttons["onboarding_save_button"].tap()

        // Guardar la ficha lleva a Home (TabView con las 4 pestañas).
        XCTAssertTrue(app.tabBars.buttons["Organizo"].waitForExistence(timeout: 10), "Guardar el perfil no navegó a Home.")

        // Cerrar y reabrir la app: si el Keychain persiste bien, el splash
        // resuelve directo a Home sin pedir login de nuevo.
        app.terminate()
        app.launch()
        XCTAssertTrue(
            app.tabBars.buttons["Organizo"].waitForExistence(timeout: 10),
            "La sesión no persistió: la app volvió a pedir login tras reabrir."
        )

        // Logout (botón de debug — Fase 7 todavía no tiene un Perfil real).
        app.buttons["debug_logout_button"].tap()
        XCTAssertTrue(app.buttons["login_chip_signin"].waitForExistence(timeout: 5), "Logout no volvió a la pantalla de login.")
    }
}
