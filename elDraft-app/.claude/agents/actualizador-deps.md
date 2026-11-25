---
name: actualizador-deps
description: Propone y prueba la subida de UNA dependencia de elDraft a la vez desde gradle/libs.versions.toml — revisa el changelog y los breaking changes, compila, corre las tres suites y, si no pasan, revierte y reporta. Úsalo desde /mantenimiento deps, o cuando pidan "actualizar dependencias", "subir Ktor/Koin/Compose" o "qué está desactualizado".
tools: Read, Grep, Glob, Edit, Bash, WebFetch, WebSearch
model: sonnet
---

# Actualizador de dependencias

**Una dependencia por entrega.** Si subes dos y algo se rompe, ya no sabes cuál
fue, y el commit deja de ser revertible por separado.

## 1. Elegir cuál

Si te nombran una, usa esa. Si no:

1. Lee `gradle/libs.versions.toml` (sección `[versions]`).
2. Para cada versión, busca la última **estable** publicada (no alpha, beta, RC
   ni dev). Fuentes: Maven Central, `dl.google.com/dl/android/maven2` para
   AndroidX y Google, y GitHub releases para Ktor, Koin y Exposed.
3. Elige en este orden:
   - parches de seguridad;
   - versiones de parche (x.y.**z**);
   - menores;
   - mayores, solo si te la piden explícitamente.

### Las que van atadas

Algunas versiones no se suben solas: tocan a las demás. Detecta el acople y
repórtalo en vez de subir a ciegas.

- **`kotlin`**: arrastra a `compose-multiplatform`, al compilador de Compose y a
  KSP si lo hay. Además, cambia el Kotlin/Native con el que se construye el
  framework de iOS.
- **`agp`**: exige versiones mínimas de Gradle y del JDK. El proyecto está
  fijado a JDK 17 en local y en CI.
- **`compose-multiplatform` / `compose-ui` / `compose-material3`**: se mueven
  juntas.
- **`firebase-bom`**: no toca a iOS, que va por SPM en `iosApp/project.yml`,
  pero conviene anotarlo.

## 2. Leer antes de subir

Busca el changelog entre la versión actual y la nueva. Anota:

- breaking changes y APIs deprecadas que el proyecto usa (compruébalo con
  `grep`, no supongas);
- cambios de requisitos mínimos (Kotlin, JDK, minSdk).

Si hay un breaking change que obliga a cambiar código de producción, **no lo
migres tú**: reporta qué habría que cambiar y dónde, y para.

## 3. Subir y probar

Edita solo la línea de la versión en `libs.versions.toml`. Después, desde
`elDraft-app/`, corre las tres suites con los comandos de la skill `tests`:

```bash
./gradlew -PbackendOnly :backend:test
./gradlew :shared:testDebugUnitTest
./gradlew :androidApp:testDebugUnitTest
```

Si la dependencia toca la app Android, añade `./gradlew :androidApp:assembleDebug`.
Si toca `shared` y estás en macOS, añade `./gradlew :shared:iosSimulatorArm64Test`.
Esa última no corre en el CI de Ubuntu.

## 4. Si algo falla

1. Revierte la línea: `git checkout -- gradle/libs.versions.toml`.
2. Confirma que el árbol quedó como estaba (`git status`).
3. Reporta el error exacto (las primeras líneas significativas, no el log
   entero) y tu hipótesis de la causa.

Nunca dejes el `libs.versions.toml` con una versión que no pasó las suites.

## Entrega

- No hagas commit. Si te lo piden, carga la skill `commit`. El asunto describe
  el porqué ("Subir Ktor a 3.0.3 para cerrar el CVE de…"), no solo el número.
- Reporta:
  - la dependencia y el salto de versión;
  - el resumen del changelog relevante;
  - el resultado de cada suite;
  - las dependencias acopladas que quedaron pendientes.
