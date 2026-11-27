---
name: backend-local
description: Levantar, verificar, reiniciar y diagnosticar el backend de elDraft (Ktor + PostgreSQL/PostGIS) en la máquina local — orden de arranque, chequeos previos (Docker, puertos 5432/8080, JDK 17, local.properties), healthcheck, resets de la base de datos, cómo apuntar el emulador o un dispositivo físico, y tabla de síntomas con su causa. Úsala siempre que pidan "levantar el backend", "correr el servidor local", "arrancar la DB", cuando falle `:backend:run` (Connection refused, puerto en uso, esquema viejo), cuando la app no conecte con el backend local, o antes de probar cualquier cambio de backend a mano.
---

# Backend local de elDraft

Todo se corre desde `elDraft-app/`. El backend es Ktor (Netty) en `:8080` y necesita
PostgreSQL 16 + PostGIS en `:5432`, que vive en Docker.

## Orden de arranque (importa)

1. **Base de datos primero.** Si Ktor arranca antes, HikariCP falla con
   `Connection to localhost:5432 refused`.
2. **Backend después.**
3. **Verificar** con `/health`.

```bash
# 0. Chequeos previos
docker info >/dev/null 2>&1 || echo "Docker apagado: abre Docker Desktop"
lsof -nP -iTCP:5432 -sTCP:LISTEN     # lo esperado: solo eldraft-postgres (o nada)
lsof -nP -iTCP:8080 -sTCP:LISTEN     # debe estar vacío antes de arrancar
java -version 2>&1 | head -1         # 17+ (el proyecto apunta a 17; 21 verificado en backend)

# 1. Base de datos
docker compose up -d
until [ "$(docker inspect -f '{{.State.Health.Status}}' eldraft-postgres)" = healthy ]; do sleep 2; done

# 2. Backend (en foreground o run_in_background; el proceso no termina solo)
./gradlew -PbackendOnly :backend:run

# 3. Verificar
curl -s http://localhost:8080/health      # {"status":"ok"}
```

Notas:
- `-PbackendOnly` excluye `:shared` y `:androidApp` de la configuración de Gradle,
  así que no hace falta Android SDK ni `google-services.json` para el backend.
  Sin el flag también funciona, pero configura todo el proyecto y es más lento.
- En Apple Silicon Postgres corre emulado (amd64). El primer arranque tarda más;
  espera al healthcheck, no a un `sleep` fijo.
- Verificado en esta máquina: con JDK 21 y `-PbackendOnly` arranca en pocos segundos (Docker Desktop hubo que abrirlo antes: `open -a Docker`).
- Si lo lanzas en background, confirma que está arriba con `/health` en lugar de
  asumirlo: Gradle imprime "Responding at" pero conviene verificar de verdad.

## Qué configura `:backend:run` por ti

- `KTOR_DEVELOPMENT=true`: sin esto, `Application.kt` **aborta** al ver secretos
  por defecto o `authMode=mock`. Solo `:backend:run` lo pone; si arrancas el
  backend por otra vía (IDE, `installDist`, jar) y aborta, es por esto.
- `FIREBASE_SERVICE_ACCOUNT_PATH` se toma de `local.properties` si existe. Sin él,
  el backend arranca igual pero **no envía push FCM**.
- `firebase.authMode = mock` por defecto: el login acepta un token de desarrollo
  sin verificar contra Google. Para probar Firebase real:
  `FIREBASE_AUTH_MODE=firebase` + service account.

Valores locales por defecto (no hay que exportar nada): DB `eldraft`/`eldraft`/`eldraft`
en `localhost:5432`, `jwt.secret` de desarrollo, zona horaria `America/Bogota`.

## Resets de la base de datos

| Quiero | Comando |
|---|---|
| Pausar la DB conservando datos | `docker compose stop` |
| Retomar | `docker compose up -d` |
| Resetear **todo** (esquema + datos) | `docker compose down -v` y luego `up -d` |
| Vaciar convocatorias/ratings/asistencias sin borrar el volumen | `DATABASE_RESET_ON_START=true ./gradlew -PbackendOnly :backend:run` (una vez; no lo dejes exportado) |
| Reiniciar solo el backend | detener el proceso de Gradle y volver a `:backend:run` |

`down -v` y `DATABASE_RESET_ON_START` son destructivos: confirma con el usuario
antes de ejecutarlos si hay datos que pudieran importarle.

El backend crea la extensión PostGIS y sincroniza el esquema al arrancar, así que
tras un `down -v` no hay migraciones que correr.

## Diagnóstico

| Síntoma | Causa probable | Arreglo |
|---|---|---|
| `Connection to localhost:5432 refused` | DB no está arriba o aún no está healthy | `docker compose up -d`, esperar healthcheck, relanzar |
| `Cannot connect to the Docker daemon` | Docker Desktop apagado | Abrirlo y esperar |
| `java.net.BindException: Address already in use` en 8080 | Otro backend (o proceso Gradle huérfano) | `lsof -nP -iTCP:8080 -sTCP:LISTEN`, matar ese PID; ojo que no sea otra cosa tuya |
| `bind: address already in use` al subir Postgres (5432) | Un Postgres local (brew) ocupa el puerto | Detener ese Postgres (`brew services stop postgresql@…`) o cambiar el puerto del compose |
| Backend aborta con "Configuración insegura…" | Arrancado sin `KTOR_DEVELOPMENT=true` | Usar `:backend:run`, o exportar `KTOR_DEVELOPMENT=true` |
| Errores de esquema/columna inexistente tras cambiar modelos | Volumen con esquema viejo | `docker compose down -v` y `up -d` |
| Convocatorias recién creadas no aparecen en `nearby` | Desfase de zona horaria (`scheduled_at` es hora local sin offset) | No cambiar `app.timezone`; el default `America/Bogota` es el correcto |
| `Unsupported class file major version` / errores de Kotlin raros | JDK demasiado viejo o nuevo | Usar JDK 17 (`JAVA_HOME`); 21 funciona para el backend |
| `SDK location not found` | Se corrió sin `-PbackendOnly` y falta `sdk.dir` | Añadir `-PbackendOnly` |
| Backend arriba pero sin push | Falta `FIREBASE_SERVICE_ACCOUNT_PATH` en `local.properties` | Esperado en local; añadirlo solo si necesitas probar FCM |

## Apuntar la app al backend local

- **Emulador Android:** funciona sin tocar nada (`10.0.2.2:8080`).
- **Dispositivo físico:** `DEV_HOST=<IP del Mac en la WiFi>` en `local.properties`
  y reconstruir la app (es un `buildConfigField`, no se lee en runtime). Mac y
  teléfono en la misma red. Si cambia la IP del Mac (otra WiFi, DHCP), hay que
  actualizarla y recompilar.
- El tráfico es HTTP/WS en claro (`http://`, `ws://`) solo en debug.

## Verificar a mano

- Salud: `curl -s localhost:8080/health`.
- Camino completo (Firebase → JWT → PostGIS → WebSocket): `scripts/smoke-test.sh`
  recibe `BASE_URL`, `FIREBASE_API_KEY`, `SMOKE_EMAIL` y `SMOKE_PASSWORD`. Está
  pensado para el backend desplegado y exige Firebase real; contra el backend
  local en modo `mock` no aplica tal cual. Para una verificación local, `/health`
  más un login con el token mock suele bastar.
- Inspeccionar la DB: `docker exec -it eldraft-postgres psql -U eldraft -d eldraft`.

## Al terminar

Si lo levantaste tú en esta sesión, avisa al usuario de que quedó corriendo (y en
qué puerto) y no lo detengas a menos que lo pida. Para apagar todo:
detener el proceso del backend y `docker compose stop`.
