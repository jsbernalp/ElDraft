---
name: arquitectura
description: Recorrido end-to-end para añadir o cambiar una feature en el monorepo KMP de elDraft — qué capa se toca primero (backend → shared → androidApp), qué archivo se crea en cada paso, los tres registros de inyección de dependencias que si se olvidan compilan pero revientan en runtime, y las reglas propias del proyecto sobre esquema de base de datos, errores y qué lógica puede vivir en un ViewModel. Úsala siempre que se vaya a añadir o modificar un endpoint, un caso de uso, un repositorio, una pantalla, un campo o una tabla; cuando haya que decidir en qué módulo vive un pedazo de código; o antes de planear cualquier cambio que cruce más de un módulo — aunque nadie diga la palabra "arquitectura".
---

# Arquitectura de elDraft — cómo atraviesa una feature el monorepo

Monorepo Kotlin Multiplatform con backend propio. Cuatro módulos:

| Módulo | Qué es | Lenguaje |
|---|---|---|
| `backend/` | API Ktor + Exposed sobre PostgreSQL/PostGIS | Kotlin JVM |
| `shared/` | **Toda la lógica de negocio del cliente** | Kotlin Multiplatform |
| `androidApp/` | App Android, solo presentación | Kotlin + Compose |
| `iosApp/` | App iOS, solo presentación | Swift + SwiftUI |

El mapa detallado de paquetes y dependencias está en `references/mapa.md`. Léelo
cuando necesites ubicar código existente; para añadir algo nuevo, sigue el
recorrido de abajo.

Las rutas de este documento van abreviadas desde la raíz del paquete. El prefijo
real es:

| Abreviatura | Ruta completa |
|---|---|
| `shared/…` | `shared/src/commonMain/kotlin/com/eldraft/…` |
| `androidApp/…` | `androidApp/src/main/kotlin/com/eldraft/android/…` |
| `backend/…` | `backend/src/main/kotlin/com/eldraft/backend/…` |
| `iosApp/…` | `iosApp/iosApp/…` |

## La regla que ordena todo lo demás

**La lógica de negocio vive en `shared`, nunca en un ViewModel.**

Un ViewModel existe para exponer estado a la UI y nada más. En cuanto le metes
una decisión de negocio —una validación, aritmética de fechas, qué partidos
siguen vigentes— condenas a iOS a reimplementarla en Swift, y a partir de ahí las
dos apps se separan en silencio. Ya pasó y ya se corrigió: `MatchTiming.kt` y
`ScheduleDefaults.kt` nacieron dentro de pantallas de Compose y hubo que mudarlos
a `shared` para que iOS pudiera reutilizarlos.

Si te descubres escribiendo un `if` que decide algo del dominio dentro de un
`ViewModel` o de un `@Composable`, ese código pertenece a un caso de uso o a un
objeto de dominio en `shared`.

## El orden de las capas

Backend → shared → androidApp → (iOS, fase aparte).

No es arbitrario: cada capa consume el contrato de la anterior. El DTO que
serializa el backend **es** el contrato; si empiezas por la pantalla, terminas
inventando un modelo que luego no cuadra con lo que responde el servidor y
rehaces el trabajo.

iOS va siempre después, en su propia fase, portando lo que Android ya dejó
probado. Ese port tiene su propia checklist — usa la skill `paridad-ios` cuando
llegue el turno.

---

## Paso 1 — backend

Cinco archivos, en este orden. El detalle de cada patrón (tipos `Record`, repos
`open`, PostGIS, zona horaria) está en `references/backend.md`.

1. **`db/tables/Tables.kt`** — la columna o tabla nueva.
2. **`repository/XRepository.kt`** — acceso a datos. La clase es `open` y sus
   métodos también: así los tests la sustituyen sin levantar una base de datos.
3. **`service/XService.kt`** — reglas de negocio y autorización. Lanza
   excepciones tipadas (`PostulationForbidden`, `ConvocatoryConflict`…), nunca
   responde HTTP.
4. **`routes/XRoutes.kt`** — DTOs `@Serializable`, `call.currentUserId()` y
   delegación al servicio. La ruta es delgada a propósito. Si necesitas
   `parseUuid`, cópialo: es una función privada duplicada en cada archivo de
   rutas, no un helper compartido.
5. **`di/BackendModule.kt` + `plugins/Routing.kt`** — registrar el servicio y
   colgar las rutas de `/api/v1`.

Si creaste una excepción tipada nueva, mapéala en `plugins/StatusPages.kt` al
status HTTP que le toca. Sin eso viaja como un 500 genérico y el cliente muestra
"El servidor tuvo un problema" en vez del motivo real.

### La trampa del esquema

No hay migraciones: el arranque llama a `createMissingTablesAndColumns`. Eso
significa que **una columna nueva tiene que ser `.nullable()` o tener
`.default(...)`**. Un `NOT NULL` sin default no se puede añadir a una tabla que
ya tiene filas, y Exposed no falla de forma obvia: la columna simplemente no
aparece y el error sale después, en runtime, lejos de la causa. Esa trampa ya
costó tener que activar un reset destructivo de la base para poder desplegar.

---

## Paso 2 — shared

Aquí está el grueso del trabajo y es donde más fácil se olvida un paso.

1. **`data/models/Models.kt`** — el modelo `@Serializable` que refleja el DTO del
   backend. Campos opcionales con valor por defecto, para que un backend más
   nuevo no rompa un cliente viejo.
2. **`data/remote/XApi.kt`** — extiende `BaseApi`, que ya inyecta la URL base y
   el header `Authorization` mediante `auth()`. Nunca construyas el header a mano.
3. **`domain/repository/XRepository.kt`** — la interfaz. Cada método con
   `@Throws(Exception::class)` (ver abajo por qué).
4. **`data/repository/XRepositoryImpl.kt`** — implementación, normalmente
   delegación directa a la API.
5. **`domain/usecase/<feature>/XUseCase.kt`** — una clase por operación, con
   `operator fun invoke(...)`. Aquí van las validaciones (`require(...)`) y la
   orquestación real.
6. **`core/di/SharedModule.kt`** — registrar API, repositorio y casos de uso.
7. **`core/di/KoinAccessors.kt`** — añadir el accessor al objeto `IosKoin`. Ojo:
   este archivo está en el source set **`iosMain`**, no en `commonMain`
   (`shared/src/iosMain/kotlin/com/eldraft/core/di/`).

### Los tres registros de DI

Estos son los olvidos que más caro salen, porque **compilan sin quejarse** y
fallan en runtime:

| Archivo | Qué se registra | Qué pasa si falta |
|---|---|---|
| `shared/core/di/SharedModule.kt` | API, repositorio, caso de uso | Koin lanza `NoDefinitionFoundException` al abrir la pantalla |
| `shared/src/iosMain/…/core/di/KoinAccessors.kt` (`IosKoin`) | Accessor del caso de uso | Swift no puede resolverlo: el port a iOS se traba |
| `androidApp/di/AndroidModule.kt` | `viewModelOf(::XViewModel)` | La pantalla crashea al navegar hacia ella |

### `@Throws(Exception::class)` no es decorativo

En las interfaces de `domain/repository` y en los casos de uso, cada método
`suspend` que pueda fallar lleva `@Throws(Exception::class)`. Sin esa anotación,
Kotlin/Native no declara el error al exportar el framework y **una excepción real
mata la app de iOS** en vez de propagarse como un `throw` que Swift pueda
capturar. Se ha tenido que corregir a posteriori en varias fases; ponlo desde el
principio aunque iOS todavía no consuma el método.

### Errores

El camino completo, de punta a punta:

```
Service lanza PostulationForbidden
  → StatusPages responde 403 {"code":"FORBIDDEN","message":"..."}
  → ApiException lo parsea (status + code + userMessage)
  → el ViewModel hace e.userMessage("texto de respaldo")
```

Por eso los ViewModels no arman mensajes de error propios: el texto bueno lo
escribe el backend y `userMessage` solo pone un respaldo por si no hay cuerpo
legible. Y por eso los mensajes de error de ViewModels y de `shared` siguen en
español y no pasan por `strings.xml` — están en un módulo KMP sin acceso a
recursos de Android (esto está explicado en `CLAUDE.md`).

---

## Paso 3 — androidApp

1. **`ui/<feature>/XViewModel.kt`** — un `data class XUiState`, un
   `MutableStateFlow` privado y su `StateFlow` público. Recibe casos de uso por
   constructor. Los errores se capturan con `e.userMessage("respaldo")`.
2. **`ui/screens/XScreen.kt`** — Compose puro, sin lógica de negocio.
3. **`res/values/strings.xml`** — todo texto visible.
4. **`di/AndroidModule.kt`** — `viewModelOf(::XViewModel)`.
5. **`ui/ElDraftApp.kt`** — un objeto en `sealed class Screen` y su `composable`
   en el `NavHost`. Las rutas con argumento llevan un helper `route(id)` para no
   construir strings a mano en las llamadas.

Design tokens y strings tienen reglas obligatorias, un linter y un hook
pre-commit. Están en `CLAUDE.md`, que ya se carga en cada sesión — no las
repitas ni las reinterpretes desde aquí.

---

## Antes de dar la feature por terminada

- ¿Las columnas nuevas son nullable o tienen default?
- ¿La excepción tipada nueva está mapeada en `StatusPages`?
- ¿Los métodos nuevos de repositorio y casos de uso llevan `@Throws`?
- ¿Están los tres registros de DI (`SharedModule`, `IosKoin`, `AndroidModule`)?
- ¿La lógica de negocio quedó en `shared` y no en el ViewModel?
- ¿Los textos salen de `strings.xml` y el diseño de `ElDraftTheme.*`?
- ¿Hay tests de lo que decide algo? (`shared` y `backend` tienen suite propia; un
  test roto frena el deploy de Railway.)
- ¿Queda anotado qué falta para iOS?

## Valores de dominio

`shared/data/models/DomainCatalogs.kt` es la fuente única de los formatos
(`"Fútbol 5"`…), posiciones, contexturas, pies hábiles y ambientes. Son datos que
viajan al backend, no etiquetas de presentación: no se traducen, no se duplican
en la UI y no se convierten en enums de Swift.
