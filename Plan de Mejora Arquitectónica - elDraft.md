# Plan de Mejora Arquitectónica — elDraft

> **Estado:** Propuesta (sin codificar todavía)
> **Fecha:** 2026-06-07
> **Alcance acordado:** Arquitectura completa (Clean) → DI + Repository + UseCases/Dominio + Modularización
> **Framework de DI:** **Koin** (multiplataforma — KMP/Compose/Ktor)

---

## 1. Por qué Koin y no Hilt

El usuario preguntó por **Hilt**, pero hay una restricción técnica de fondo que define la decisión:

| Criterio | Hilt | Koin |
|---|---|---|
| Funciona en `commonMain` (KMP) | ❌ No (Android/JVM-only, usa Dagger + APT) | ✅ Sí |
| Funciona en `androidApp` | ✅ Sí | ✅ Sí |
| Funciona en el backend **Ktor** | ❌ No | ✅ Sí (`koin-ktor`) |
| ViewModels portables a iOS | ❌ No | ✅ Sí |
| Ya está en el `libs.versions.toml` | — | ✅ (`koin-android`, `koin-compose`, `koin-ktor`, `koin-core`) |
| Annotation processing obligatorio | ✅ (kapt/ksp) | ❌ Opcional |

**elDraft es Kotlin Multiplatform** (el módulo `shared` ya declara targets `iosX64/iosArm64/iosSimulatorArm64` y usa `ktor-client-darwin`). Hilt rompería esa portabilidad: tendríamos un framework para Android, nada para el shared y nada para el backend. Koin nos da **un solo framework para los tres** (`shared`, `androidApp`, `backend`) y ya está declarado en el catálogo de versiones. Incluso `ElDraftApplication` tiene el comentario: *"Más adelante se puede migrar a Koin si la grafo de dependencias crece."*

**Decisión: Koin.**

---

## 2. Diagnóstico de la arquitectura actual

### 2.1 Lo que existe hoy

```
shared/commonMain
  └── data/api/ElDraftApi.kt      ← cliente HTTP monolítico (todos los endpoints)
  └── data/models/Models.kt       ← DTOs serializables

androidApp
  └── ElDraftApplication.kt       ← service locator manual (api, session, googleAuth)
  └── ui/ViewModelFactories.kt    ← viewModelFactory manual + elDraftViewModel()
  └── ui/auth/AuthViewModel.kt    ← llama api + session + googleAuth directamente
  └── ui/profile/ProfileViewModel.kt ← llama api + session directamente
  └── data/SessionManager.kt      ← DataStore (Android)
  └── data/GoogleAuthClient.kt    ← Credential Manager (Android)

backend
  └── repository/UserRepository.kt ← único repo; el resto son objetos/funciones sueltas
  └── routes/*.kt                  ← lógica de negocio dentro de las rutas
```

### 2.2 Problemas concretos (no teóricos)

1. **Service locator manual** (`ElDraftApplication` + `ViewModelFactories`): cada ViewModel nuevo obliga a tocar `elDraftViewModelFactory` a mano. No escala y no es testeable sin Android framework.
2. **ViewModels acoplados a la capa de transporte:** `AuthViewModel` y `ProfileViewModel` conocen `ElDraftApi`, `SessionManager` *y* `GoogleAuthClient`. Mezclan orquestación de UI con detalles de red/persistencia.
3. **Lógica de negocio repartida:** p. ej. "guardar perfil" hace `updatePhone` + `updateProfile` + manejo de token dentro del ViewModel (`ProfileViewModel.saveProfile`). Eso es un caso de uso, no responsabilidad de la UI.
4. **`ElDraftApi` monolítico:** un solo archivo con todos los endpoints (auth, players, convocatories, postulations, attendance, websocket). Crecerá sin control en Fase 2/3.
5. **Token manejado por estado mutable** (`ElDraftApi.authToken` + `setToken`) y re-seteado manualmente en cada ViewModel (`session.currentToken()?.let { api.setToken(it) }`). Frágil y repetido.
6. **Backend sin capa de servicios consistente:** la lógica vive en las rutas; solo `UserRepository` existe como repo.

---

## 3. Arquitectura objetivo (Clean, pragmática para MVP)

Capas y regla de dependencia (las flechas apuntan hacia adentro; el dominio no depende de nada):

```
┌──────────────────────────────────────────────────────────────┐
│  PRESENTATION (androidApp / iosApp)                            │
│   Compose Screens → ViewModels (estado UI)                     │
│                       │ dependen de ↓                          │
├──────────────────────────────────────────────────────────────┤
│  DOMAIN (shared/commonMain)                                    │
│   UseCases (1 acción de negocio) · Repository (interfaces) ·   │
│   Modelos de dominio · Result<T> / errores de dominio         │
│                       ▲ implementadas por ↓                    │
├──────────────────────────────────────────────────────────────┤
│  DATA (shared/commonMain + platform)                          │
│   RepositoryImpl · DataSources (remoto = Ktor, local =        │
│   DataStore) · DTOs + mappers DTO↔dominio                     │
└──────────────────────────────────────────────────────────────┘
```

**Principio rector para un MVP:** Clean *pragmático*. No metemos UseCases por metro cuadrado; los creamos cuando hay **orquestación real** (≥2 fuentes o reglas), y dejamos passthrough directo Repository→ViewModel cuando el caso de uso sería un mero proxy. Evitamos sobre-ingeniería.

### 3.1 Estructura de paquetes propuesta (shared/commonMain)

```
com.eldraft
├── core
│   ├── di/                 ← módulos Koin del shared (sharedModule, networkModule)
│   ├── network/
│   │   ├── HttpClientFactory.kt   ← crea el HttpClient (config común)
│   │   ├── ApiException.kt        ← (movido desde ElDraftApi)
│   │   └── AuthTokenProvider.kt   ← interfaz: provee el token actual al cliente
│   └── result/
│       └── DomainResult.kt        ← Result<T> / DomainError sellado
├── data
│   ├── remote/
│   │   ├── AuthApi.kt             ← split de ElDraftApi por feature
│   │   ├── PlayerApi.kt
│   │   ├── ConvocatoryApi.kt
│   │   ├── PostulationApi.kt
│   │   └── AttendanceApi.kt
│   ├── local/
│   │   └── SessionStore.kt        ← interfaz (impl en androidApp con DataStore)
│   ├── dto/                       ← DTOs (lo que hoy está en Models.kt de red)
│   ├── mapper/                    ← DTO ↔ dominio
│   └── repository/
│       ├── AuthRepositoryImpl.kt
│       ├── ProfileRepositoryImpl.kt
│       └── ConvocatoryRepositoryImpl.kt
└── domain
    ├── model/                     ← modelos de dominio (PlayerProfile, etc.)
    ├── repository/                ← interfaces: AuthRepository, ProfileRepository…
    └── usecase/
        ├── auth/   SignInWithGoogleUseCase, SignInDevUseCase, ObserveSessionUseCase
        ├── profile/ SaveProfileUseCase, GetPlayerCromoUseCase
        └── convocatory/ (Fase 2)
```

### 3.2 Android (androidApp)

```
com.eldraft.android
├── ElDraftApplication.kt   ← startKoin { androidContext(this); modules(...) }
├── di/AndroidModule.kt     ← SessionStore (DataStore), GoogleAuthClient, ViewModels
├── data/
│   ├── DataStoreSessionStore.kt   ← impl de SessionStore (Android)
│   └── GoogleAuthClient.kt        ← (se queda; Android-only por Credential Manager)
└── ui/ (sin cambios estructurales; usa koinViewModel())
```

`ViewModelFactories.kt` y `elDraftViewModel()` **se eliminan** → reemplazados por `koinViewModel<T>()`.

### 3.3 Backend (Ktor)

- Introducir `koin-ktor` con `install(Koin) { modules(backendModule) }`.
- Capa de **servicios** por feature: `AuthService`, `PlayerService`, `ConvocatoryService`… que encapsulan la lógica que hoy vive en las rutas.
- Repositorios por agregado (ya existe `UserRepository`; añadir los que falten conforme avanzan las fases).
- Rutas quedan delgadas: parsean request → delegan en service → serializan response.

---

## 4. Patrones clave a introducir

### 4.1 Token de auth como `AuthTokenProvider` (elimina `setToken` manual)

En lugar de `api.setToken()` repartido por los ViewModels, el `HttpClient` se configura con un proveedor que lee el token del `SessionStore` en cada request:

```kotlin
// core/network/AuthTokenProvider.kt (domain-ish, en shared)
fun interface AuthTokenProvider { suspend fun currentToken(): String? }

// core/network/HttpClientFactory.kt
fun createHttpClient(tokenProvider: AuthTokenProvider, json: Json) = HttpClient {
    install(ContentNegotiation) { json(json) }
    install(WebSockets)
    expectSuccess = true
    install(Auth) {
        bearer { loadTokens {
            tokenProvider.currentToken()?.let { BearerTokens(it, "") }
        } }
    }
    // … HttpResponseValidator → ApiException (igual que hoy)
}
```

Beneficio: ningún ViewModel vuelve a llamar `setToken`; el token siempre sale de la fuente de verdad (`SessionStore`).

### 4.2 `DomainResult<T>` en lugar de try/catch repetido

```kotlin
sealed interface DomainError {
    data class Network(val cause: Throwable) : DomainError
    data class Http(val status: Int, val body: String) : DomainError
    data object Unauthorized : DomainError
    data class Unknown(val cause: Throwable) : DomainError
}
typealias DomainResult<T> = Result<T> // o un sealed propio; a decidir en impl
```

Los repos traducen `ApiException` → `DomainError`; los ViewModels mapean a estado UI. Hoy cada ViewModel hace su propio `catch (e: Exception)` con strings sueltos.

### 4.3 ViewModels delgados con UseCases

Antes (`ProfileViewModel.saveProfile`): el ViewModel conoce `api`, `session`, hace `updatePhone` + `updateProfile`, gestiona token.
Después:

```kotlin
class ProfileViewModel(
    private val saveProfile: SaveProfileUseCase,
    private val getCromo: GetPlayerCromoUseCase,
) : ViewModel() { /* solo orquesta estado UI */ }

// domain/usecase/profile/SaveProfileUseCase.kt — la orquestación vive aquí
class SaveProfileUseCase(private val repo: ProfileRepository) {
    suspend operator fun invoke(input: SaveProfileInput): DomainResult<PlayerProfile> =
        repo.saveProfile(input) // repo internamente hace phone + profile
}
```

---

## 5. Plan de ejecución por fases (incremental, sin romper la app)

Cada paso compila y deja la app funcionando. **Orden recomendado:**

### Paso 0 — Preparación (sin cambios de comportamiento)
- [ ] Añadir al `libs.versions.toml`: `koin-test`, y (opcional) `koin-annotations` + KSP si más adelante queremos anotaciones. Para empezar usamos **Koin DSL puro** (sin KSP) para no añadir build complexity.
- [ ] Confirmar que `koin-core` está en `shared/commonMain` (ya está).

### Paso 1 — DI en Android reemplazando el service locator
- [ ] `ElDraftApplication`: `startKoin { androidContext(this@…); modules(sharedModule, androidModule) }`.
- [ ] Crear `androidModule` con: `SessionStore` (DataStore impl), `GoogleAuthClient`, y los ViewModels (`viewModelOf(::AuthViewModel)`, `viewModelOf(::ProfileViewModel)`).
- [ ] Crear `sharedModule`/`networkModule` con: `Json`, `AuthTokenProvider`, `HttpClient`, y las APIs.
- [ ] Pantallas: cambiar `elDraftViewModel()` → `koinViewModel()`.
- [ ] Eliminar `ViewModelFactories.kt` y el service locator manual de `ElDraftApplication`.
- [ ] **Verificar:** login dev + login Google + onboarding + cromo siguen funcionando en emulador.

### Paso 2 — Split de `ElDraftApi` y capa de red en `core/network`
- [ ] Mover `ApiException` a `core/network`.
- [ ] Crear `HttpClientFactory` con `AuthTokenProvider` (elimina `setToken`).
- [ ] Partir `ElDraftApi` en `AuthApi`, `PlayerApi`, `ConvocatoryApi`, `PostulationApi`, `AttendanceApi`.
- [ ] **Verificar:** mismos flujos siguen funcionando.

### Paso 3 — Capa Repository (interfaces en domain, impl en data)
- [ ] `domain/repository`: `AuthRepository`, `ProfileRepository`.
- [ ] `data/repository`: impls que usan las *Api y el `SessionStore`.
- [ ] DTOs vs modelos de dominio + mappers (si difieren; si son idénticos, dejar passthrough y documentarlo).
- [ ] ViewModels pasan a depender de repos (todavía sin UseCases).
- [ ] **Verificar.**

### Paso 4 — UseCases donde aporten (orquestación real)
- [ ] `SignInWithGoogleUseCase`, `SignInDevUseCase`, `ObserveSessionUseCase`.
- [ ] `SaveProfileUseCase` (encapsula phone+profile), `GetPlayerCromoUseCase`.
- [ ] ViewModels dependen de UseCases; quedan delgados (solo estado UI).
- [ ] **Verificar.**

### Paso 5 — DI en el backend (Ktor + koin-ktor)
- [ ] `install(Koin) { modules(backendModule) }`.
- [ ] Servicios por feature; rutas delgadas.
- [ ] **Verificar:** correr la suite de curl de Fase 1 (los 10 tests).

### Paso 6 — Tests
- [ ] `koin-test` + tests de módulos (verificar que el grafo resuelve).
- [ ] Tests unitarios de UseCases con repos fake (sin Android framework, en `commonTest`).
- [ ] Tests de repos con un `MockEngine` de Ktor.

### Paso 7 (opcional, futuro) — Modularización por Gradle
- Si el código crece, partir `shared` en `:core`, `:feature:auth`, `:feature:profile`, `:feature:convocatory`. **No** lo hacemos ahora: para el tamaño actual sería sobre-ingeniería. Lo dejamos anotado como evolución.

---

## 6. Cambios en `libs.versions.toml`

Ya disponible: `koin-core`, `koin-android`, `koin-compose`, `koin-ktor` (v4.0.0).

A añadir:
```toml
# DI - testing
koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }
```
Ktor `MockEngine` para tests de repos:
```toml
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
```

> Nota: arrancamos con **Koin DSL puro** (sin `koin-annotations`/KSP) para no añadir un procesador de anotaciones al build. Si la cantidad de definiciones se vuelve tediosa, migramos a `@KoinViewModel`/`@Single` con KSP en un paso posterior.

---

## 7. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Romper el login que ya funciona | Migración incremental por pasos; verificar en emulador tras cada paso |
| Koin resuelve el grafo en runtime (errores no en compile-time) | Test de `checkModules()` / `verify()` en CI; KSP opcional luego para chequeo en compilación |
| Sobre-ingeniería para un MVP | UseCases solo donde hay orquestación real; passthrough cuando no aporta; modularización Gradle aplazada |
| `bearer` Auth de Ktor + refresh | Por ahora solo `loadTokens`; el refresh/expiración se aborda cuando el backend emita refresh tokens |
| Divergencia DTO/dominio innecesaria | Si DTO == modelo de dominio, mantener passthrough y documentarlo; no duplicar por dogma |

---

## 8. Resultado esperado

- **Un solo framework de DI (Koin)** en shared, Android y backend.
- ViewModels **testeables** y delgados (dependen de abstracciones, no de Ktor/DataStore).
- `ElDraftApi` monolítico → APIs por feature + capa Repository.
- Token de auth centralizado (sin `setToken` manual repartido).
- Base lista para **Fase 2/3** sin deuda estructural creciente.
- ViewModels y lógica de negocio **portables a iOS** (siguen en `commonMain`).

---

## 9. Decisión pendiente del usuario

Antes de codificar, confirmar:
1. ¿Arrancamos por el **Paso 1 (DI en Android)** y avanzamos secuencial?
2. ¿OK con **Koin DSL puro** primero (sin KSP/anotaciones)?
3. ¿Incluimos el **backend (Paso 5)** en esta tanda o lo dejamos para después de Fase 2?
