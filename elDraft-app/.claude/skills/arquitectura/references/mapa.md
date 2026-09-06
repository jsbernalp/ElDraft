# Mapa del repositorio

Referencia para **ubicar** código existente. Para añadir código nuevo, el
recorrido está en el `SKILL.md`.

## Dos raíces distintas

La raíz de git es `ElDraft/`, pero el proyecto Gradle y el `CLAUDE.md` viven un
nivel más abajo, en `ElDraft/elDraft-app/`. Importa para tres cosas:

- Los workflows de CI tienen que estar en `ElDraft/.github/workflows/` (GitHub no
  mira subdirectorios) y usan `working-directory: elDraft-app`.
- El hook pre-commit resuelve rutas desde la raíz de git.
- Los documentos de diseño están en la raíz de git, no en `elDraft-app/`.

## Documentos en la raíz de git

| Documento | Qué contiene |
|---|---|
| `Documento de Requerimientos_ elDraft (MVP).md` | Alcance funcional, identidad visual, modelo de negocio |
| `Plan Técnico MVP - elDraft.md` | Esquema de BD, endpoints y las **Fases de implementación** |
| `Plan de Mejora Arquitectónica - elDraft.md` | Por qué Koin y no Hilt, deuda técnica, refactors planeados |
| `Buscar Cupo - Ajustes UX.md` | Iteraciones sobre la pantalla de búsqueda |
| `Caso Borde - Organizador No Llega.md` | Diseño del flujo de no-show del organizador |
| `README.md` | Puesta en marcha, tabla de endpoints, stack |

**Estos documentos derivan del código.** El `README.md`, por ejemplo, todavía
anuncia "Compose Multiplatform" como capa de UI cuando iOS es SwiftUI nativo
desde que se quitó Compose MP de `shared/build.gradle.kts`. Cuando un documento y
el código no coincidan, **el código manda**; avísalo en vez de propagar el dato
viejo.

## Dirección de las dependencias

```
androidApp ─┐
            ├─→ shared ──→ (HTTP/WS) ──→ backend ──→ PostgreSQL + PostGIS
iosApp ─────┘
```

`shared` no conoce a nadie más. `backend` no depende de `shared`: los modelos
están duplicados a propósito a ambos lados del cable, así el servidor puede
evolucionar su DTO sin arrastrar al cliente. `androidApp` e `iosApp` no se
conocen entre sí; lo único que comparten es `shared`.

## `shared/` — dónde vive cada cosa

| Paquete | Contenido |
|---|---|
| `core/config/` | `ApiConfig` (URL base, la provee cada plataforma) |
| `core/network/` | `BaseApi`, `HttpClientFactory`, `AuthTokenProvider`, `ApiException` + `userMessage()` |
| `core/di/` | `SharedModule` (grafo Koin común) |
| `data/models/` | `Models.kt` (modelos `@Serializable`) y `DomainCatalogs.kt` |
| `data/remote/` | Una API por feature: `AuthApi`, `ConvocatoryApi`, `PostulationApi`… |
| `data/repository/` | Los `*RepositoryImpl` |
| `data/local/` | `SessionStore` (interfaz; DataStore en Android, Keychain en iOS) |
| `domain/repository/` | Las interfaces de repositorio |
| `domain/usecase/<feature>/` | Un caso de uso por operación |
| `domain/auth/` | Puertos de identidad que implementa cada plataforma |
| `domain/convocatory/`, `domain/match/` | Lógica pura de dominio (`ScheduleDefaults`, `MatchTiming`) |

Source sets: `commonMain` (todo lo anterior), `androidMain`, `iosMain`
(`IosSessionStore`, `KoinIos`, `KoinAccessors`, los providers de identidad) y
`commonTest`.

Los targets de iOS **solo se declaran en macOS** (guarda `isMacOs` en
`shared/build.gradle.kts`): en Windows o Linux el sync del IDE fallaba al
resolver las librerías Apple.

## `androidApp/` — dónde vive cada cosa

| Paquete | Contenido |
|---|---|
| `ui/screens/` | Pantallas Compose |
| `ui/components/` | Componentes reutilizables |
| `ui/map/` | Mapa, lista de convocatorias y sus sheets |
| `ui/<feature>/` | ViewModels (`ui/postulation/`, `ui/draft/`, `ui/attendance/`…) |
| `ui/theme/` | **Definición** de los design tokens; único sitio con literales de diseño |
| `di/AndroidModule.kt` | Koin de Android: ViewModels, `SessionManager`, providers |
| `data/` | Clientes de identidad (`EmailAuthClient`, `GoogleAuthClient`), `SessionManager` |
| `notifications/` | FCM, `NotificationHelper`, `NotificationRefreshBus` |
| `util/` | Ubicación, QR, navegación a apps de mapas |

Navegación: `ui/ElDraftApp.kt` (`sealed class Screen` + `NavHost` + deep links) y
`ui/MainScaffold.kt` (las pestañas).

## `iosApp/` — dónde vive cada cosa

Espeja a Android con otros nombres de carpeta: `Views/` ≈ `ui/screens/`,
`ViewModels/`, `Components/`, `Map/`, `DesignSystem/` ≈ `ui/theme/`,
`Navigation/` (`AppRoute`, `RootView`, `MainTabView`).

El proyecto Xcode **se genera con XcodeGen** desde `project.yml`; no se edita el
`.xcodeproj` a mano. Los textos viven en `Resources/Localizable.xcstrings` con
**las mismas claves** que `strings.xml` de Android.

## `backend/` — dónde vive cada cosa

| Paquete | Contenido |
|---|---|
| `db/tables/Tables.kt` | Todas las tablas Exposed |
| `repository/` | Acceso a datos; clases `open` para poder falsearlas en tests |
| `service/` | Reglas de negocio y autorización |
| `routes/` | DTOs `@Serializable` + rutas delgadas |
| `plugins/` | Auth, CORS, `Databases`, `Routing`, `StatusPages`, `Serialization`, `WebSockets`, `Scheduler` |
| `auth/` | `JwtService`, `FirebaseTokenVerifier`, `MockTokenVerifier` |
| `notifications/` | `FcmService` |
| `websocket/` | `MapWebSocket` (pines en vivo, geofiltrados) |
| `di/BackendModule.kt` | Grafo Koin del servidor |

## Autenticación, de punta a punta

1. La app se autentica con **Firebase** (email/contraseña o Google) y obtiene un
   ID token de Firebase.
2. `POST /api/v1/auth/login` verifica ese token contra Google y emite un **JWT
   propio** del backend.
3. Ese JWT es el que viaja en `Authorization: Bearer` en todas las demás
   llamadas, inyectado por `AuthTokenProvider` a través de `BaseApi.auth()`.

`firebase.authMode=mock` salta el paso 2 y acepta cualquier token: es solo para
desarrollo local. El smoke test verifica explícitamente que producción **no** esté
en modo mock, porque en ese modo cualquiera entra como quien quiera.

## Despliegue

- **Backend**: Railway despliega desde `main` con "Wait for CI" activado. El
  workflow `backend.yml` no decide desplegar, decide si el deploy tiene permiso
  de ocurrir. Un test roto en `:backend` **o en `:shared`** deja la versión fuera.
- **Android**: `android-release.yml`, solo manual (`workflow_dispatch`). Cada
  ejecución consume un `versionCode` irrecuperable.
- **iOS**: sin pipeline. Push remoto y capabilities que exigen cuenta Apple
  Developer de pago siguen diferidos.
