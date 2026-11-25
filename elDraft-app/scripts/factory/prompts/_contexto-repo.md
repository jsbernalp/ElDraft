# Contexto del repo elDraft (corridas de Claude en GitHub)

Corres desde la raíz del repo, no desde el proyecto, así que nada de
`elDraft-app/.claude/` se carga solo. Esto es lo que necesitas saber:

- **El código vive en `elDraft-app/`**: monorepo Kotlin con `backend/` (Ktor +
  Exposed + PostGIS), `shared/` (Kotlin Multiplatform), `androidApp/`
  (Compose) e `iosApp/` (SwiftUI). La raíz solo tiene documentos de producto y
  `.github/`.
- **Antes de tocar código, lee `elDraft-app/CLAUDE.md`.** Sus reglas de design
  tokens y strings son obligatorias.
- **Skills del proyecto**: `elDraft-app/.claude/skills/<nombre>/SKILL.md`.
  Ábrelas con Read cuando apliquen: `arquitectura` (capas y los tres registros
  de DI), `tests`, `paridad-ios`, `tareas` y `commit` (asunto en infinitivo en
  español; cuerpo síntoma → causa → decisión → lo que queda).
- **Roles de la factory**: `elDraft-app/.claude/agents/*.md`. Si te piden
  planear, revisar o portar, usa el del rol como guion.
- **Gradle se corre desde `elDraft-app/`**:
  `cd elDraft-app && ./gradlew -PbackendOnly :backend:test`,
  `./gradlew :shared:testDebugUnitTest`, `./gradlew :androidApp:testDebugUnitTest`.
  Linter de UI: `./scripts/check-design-tokens.sh`.
- **Nunca empujes a `main`** ni abras PRs contra otra rama que no sea `main`.
  Railway despliega el backend desde `main`.
- **Secretos**: no leas keystores, `google-services.json`,
  `GoogleService-Info*.plist` ni `local.properties`, ni imprimas variables de
  entorno. El `androidApp/google-services.json` lo pone el workflow desde un
  secret para que `:androidApp` compile: no lo edites ni lo commitees.
- Responde en español.
