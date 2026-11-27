---
name: paridad-ios
description: Portar a iOS (SwiftUI) una pantalla, flujo o feature que Android ya tiene — la checklist completa del espejo: ViewModel @Observable sobre IosKoin, View, claves de Localizable.xcstrings idénticas a strings.xml, tokens de DesignSystem, AppRoute, regeneración del proyecto con XcodeGen, y los fallos de interoperabilidad Kotlin→Swift que crashean la app en runtime sin avisar al compilar. Úsala siempre que haya que llevar algo a iOS, cerrar una fase del port, tocar archivos de iosApp/, o revisar si Android e iOS quedaron equivalentes — aunque solo digan "hazlo también en iOS".
---

# Portar una feature a iOS

iOS va siempre **después** de Android: se porta lo que Android ya dejó probado.
Eso significa que el port casi nunca inventa comportamiento — lo espeja. Cuando
te descubras decidiendo una regla de negocio nueva mientras portas, para: o ya
existe en `shared` y no la estás usando, o falta en Android también.

Los archivos Swift lo dicen explícitamente en su encabezado (`/// Espejo de
ApplicantsViewModel.kt`). Mantén esa convención: es lo que permite encontrar el
original cuando algo diverge.

## Checklist

### 1. `shared` — casi siempre hay que tocarlo

Aunque la lógica ya exista, el port suele destapar dos huecos:

- **`@Throws(Exception::class)` faltante** en los métodos del repositorio de
  dominio y en los casos de uso. Android no lo necesita; iOS sí. Sin la
  anotación, Kotlin/Native no declara el error al exportar el framework y **una
  excepción real mata la app** en lugar de llegar a Swift como un `throw`
  capturable. Ha habido que añadirlo a posteriori en las fases 6, 7 y 8: revísalo
  antes de escribir el ViewModel, no después del primer crash.
- **Accessor en `IosKoin`** (`shared/src/iosMain/kotlin/com/eldraft/core/di/KoinAccessors.kt`).
  Swift no puede llamar a `getKoin<T>()` porque los genéricos con `reified` no se
  exportan a Objective-C. Cada caso de uso que consuma la pantalla necesita su
  línea ahí.

### 2. ViewModel espejo — `iosApp/iosApp/ViewModels/XViewModel.swift`

El equivalente de `StateFlow` + `data class UiState` es `@Observable` con
propiedades `private(set)`:

```swift
@MainActor
@Observable
final class ApplicantsViewModel {
    private(set) var applicants: [Postulation] = []
    private(set) var isLoading = false
    private(set) var error: String?

    func load(convocatoryId: String) {
        isLoading = true; error = nil
        Task {
            do {
                applicants = try await IosKoin.shared.getApplicants.invoke(convocatoryId: convocatoryId)
                isLoading = false
            } catch {
                isLoading = false
                self.error = error.kotlinMessage(fallback: "No se pudieron cargar los postulantes")
            }
        }
    }
}
```

Detalles que no son opcionales:

- `@MainActor` en la clase: el estado lo lee SwiftUI.
- El `catch` de Swift recibe un `NSError` (dominio `KotlinException`), **no** un
  `KotlinThrowable`: `error as? KotlinThrowable` y `error as? ApiException`
  siempre dan `nil`, así que el mensaje del backend nunca llegaba a pantalla y
  el 403/conflicto de horario no se detectaba. Usa los helpers de
  `ErrorMessages.swift`: `error.kotlinMessage(fallback:)` para el texto,
  `error.apiException` para `status` / `code` / `scheduleConflicts`. El texto de
  respaldo debe ser **el mismo** que usa el ViewModel de Android.
- Un error que ocurre con un sheet abierto se muestra **dentro** del sheet: el
  toast de la pantalla queda debajo y no se ve (ver `TeamNameSheet`).
- Si el ViewModel de Android escucha `NotificationRefreshBus`, el de iOS también:
  hay un `NotificationRefreshBus.shared` en Swift con la misma semántica.

### 3. View — `iosApp/iosApp/Views/XView.swift`

Tokens del design system, con la misma escala que Android pero sintaxis de enum
estático:

| Android | iOS |
|---|---|
| `ElDraftTheme.spacing.lg` | `ElDraftSpacing.lg` |
| `ElDraftTheme.colors.primary` | `ElDraftColors.primary` |
| `.copy(alpha = ElDraftTheme.alpha.textSecondary)` | `.opacity(ElDraftAlpha.textSecondary)` |
| `ElDraftTheme.shape.md` | `ElDraftShapes.md` |
| `ElDraftTheme.size.iconMd` | `ElDraftSizes.iconMd` |

La regla de no quemar literales de diseño aplica igual que en Compose, pero
**el linter no cubre Swift**: `check-design-tokens.sh` solo mira los `.kt` de la
UI de Compose, y el hook pre-commit tampoco revisa `iosApp/`. En iOS la
disciplina es manual — un `padding(16)` suelto no lo va a atrapar nadie.

### 4. Textos — `iosApp/iosApp/Resources/Localizable.xcstrings`

Las claves son **las mismas** que en `androidApp/src/main/res/values/strings.xml`.
Hoy la paridad es total: las 232 claves de Android existen en iOS. Manténla.

```swift
Text("applicants_title")                       // clave directa
String(localized: "place_search_label")        // fuera de un body
String(format: String(localized: "x"), arg)    // con argumentos
```

Comprobación rápida de que no se quedó ninguna clave sin portar:

```bash
python3 -c "
import json,re
a=open('androidApp/src/main/res/values/strings.xml').read()
and_=set(re.findall(r'<(?:string|plurals) name=\"([^\"]+)\"',a))
ios=set(json.load(open('iosApp/iosApp/Resources/Localizable.xcstrings'))['strings'])
print('faltan en iOS:', sorted(and_-ios) or 'ninguna')"
```

### 5. Navegación — `iosApp/iosApp/Navigation/`

- `AppRoute.swift`: un `case` por destino, espejo de `sealed class Screen` de
  `ElDraftApp.kt`. Los argumentos van con nombre: `case applicants(convocatoryId: String)`.
- `RootView.swift`: el `case` correspondiente en `destinationView(for:)`.

No todo `Screen` de Android es un `AppRoute`: Splash, Login, Onboarding y Home
son **fases del root** (`AppPhase`), no destinos del `NavigationStack`. Respeta
esa diferencia en vez de forzar el espejo exacto.

### 6. Regenerar el proyecto — obligatorio si añadiste archivos

El `.xcodeproj` se genera desde `project.yml` **pero está versionado**: XcodeGen
enumera los archivos al generar y los escribe en el `pbxproj`. Un `.swift` nuevo
que no pase por ahí sencillamente no se compila.

```bash
cd iosApp && xcodegen generate
```

Y el `project.pbxproj` regenerado entra en el commit. Todas las fases del port
(2 a 8) lo tocaron; si tu commit añade Swift y no lo incluye, falta este paso.

### 7. Compilar y ver

El framework de Kotlin lo compila una build phase del propio proyecto
(`./gradlew :shared:embedAndSignAppleFrameworkForXcode`), así que basta con
compilar y correr en el simulador. Verifica en pantalla, no solo que compile:
el port se rompe casi siempre en runtime, no en compilación.

## Interoperabilidad Kotlin → Swift

| En Kotlin | Llega a Swift como | Cuidado |
|---|---|---|
| `suspend fun x(): T` | `try await x()` | Necesita `@Throws` para poder capturar el error |
| `operator fun invoke(a)` | `.invoke(a: …)` | El `operator` se pierde: no hay azúcar `useCase(a)` |
| Excepción | `NSError` (`KotlinException`) | Usar `error.kotlinMessage(fallback:)` / `error.apiException`; el cast directo da `nil` |
| Parámetros con default | Sin defaults | Swift debe pasarlos todos |
| `List<T>` | `[T]` | Bien |
| `Flow<T>` | No se exporta usable | Se envuelve a mano (ver `MapEventsSubscription.kt`) |

## Diferencias legítimas de plataforma

Portar no es traducir línea a línea. Estas divergencias son correctas y ya están
establecidas:

- **QR**: `CIQRCodeGenerator` (CoreImage) en vez de ZXing.
- **Escaneo**: `AVCaptureMetadataOutput` en vez de CameraX + ML Kit. El simulador
  no tiene cámara — `QRScannerView` incluye un atajo `#if DEBUG` para pegar el
  payload a mano.
- **Push remoto**: diferido. FirebaseMessaging y APNs exigen capabilities que
  necesitan cuenta Apple Developer de pago. Las notificaciones **locales** y los
  deep links sí están.
- **Sesión**: Keychain (`IosSessionStore`) en vez de DataStore.

Cuando una diferencia así aparezca, déjala explicada en el commit: la próxima
persona que compare las dos apps va a preguntarse por qué no son iguales.
