#!/bin/sh
#
# Smoke test end-to-end contra el backend desplegado.
#
# Recorre el camino crítico completo — Firebase → JWT propio → PostGIS →
# WebSocket — contra el servidor real, no contra un mock. Es la última puerta
# antes de generar cualquier build de release de Android.
#
# Uso:
#   export BASE_URL=https://tu-servicio.up.railway.app
#   export FIREBASE_API_KEY=AIza...        # la Web API Key del proyecto Firebase
#   export SMOKE_EMAIL=smoke@ejemplo.com   # usuario de prueba (email/contraseña)
#   export SMOKE_PASSWORD=...
#   ./scripts/smoke-test.sh
#
# Modo verificación de persistencia (después de reiniciar el servicio):
#   ./scripts/smoke-test.sh --verificar <id-de-convocatoria>
#
# Requisitos: curl. No usa jq — los campos que lee son cadenas planas de primer
# nivel, así que basta con grep/sed y el script corre igual en Git Bash (Windows)
# que en macOS sin instalar nada.
#
# Lo que NO cubre y hay que probar a mano en un dispositivo:
#   - Que llegue un push FCM real (necesita un token de dispositivo).
#   - El render del mapa y el flujo de UI.

set -eu

BASE_URL="${BASE_URL:-}"
FIREBASE_API_KEY="${FIREBASE_API_KEY:-}"
SMOKE_EMAIL="${SMOKE_EMAIL:-}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-}"

# Qué binario de curl usar.
#
# El curl que trae Git para Windows es la 8.8.0 con backend Schannel, y tiene un
# bug con la renegociación TLS que pide el edge de Railway: recibe el 200, y al
# leer el cuerpo aborta con "(43) A libcurl function was given a bad argument".
# El curl.exe que viene con Windows (8.14.1) ya no lo tiene. En macOS y Linux esa
# ruta no existe y se usa el curl del PATH, que no sufre el problema.
CURL=curl
if [ -x /c/Windows/System32/curl.exe ]; then
    CURL=/c/Windows/System32/curl.exe
fi

# Un dominio pelado (sin esquema) hace que curl asuma http://, y contra Railway
# eso devuelve un redirect en vez de la API — con errores que no apuntan a la
# causa. La barra final sobra porque las rutas ya empiezan por "/".
if [ -n "$BASE_URL" ]; then
    case "$BASE_URL" in
        http://*|https://*) ;;
        *) BASE_URL="https://$BASE_URL" ;;
    esac
    BASE_URL="${BASE_URL%/}"
fi

# Coordenadas de prueba: un punto en Bogotá. Da igual cuál, mientras la consulta
# de cercanía use el mismo.
LAT=4.6482837
LNG=-74.2478905

fallos=0
convocatoria_id=""

rojo=""; verde=""; amarillo=""; gris=""; fin=""
if [ -t 1 ]; then
    rojo="$(printf '\033[31m')"; verde="$(printf '\033[32m')"
    amarillo="$(printf '\033[33m')"; gris="$(printf '\033[90m')"; fin="$(printf '\033[0m')"
fi

ok()    { printf '%s  OK  %s %s\n' "$verde" "$fin" "$1"; }
fallo() { printf '%s FALLA%s %s\n' "$rojo" "$fin" "$1"; fallos=$((fallos + 1)); }
info()  { printf '%s       %s%s\n' "$gris" "$1" "$fin"; }
aviso() { printf '%s AVISO%s %s\n' "$amarillo" "$fin" "$1"; }

# Extrae un campo string de primer nivel de una respuesta JSON.
# No es un parser de JSON: sirve para "campo": "valor" y nada más.
#
# El espacio opcional alrededor de los dos puntos no es cosmético: el backend
# serializa con prettyPrint, así que sus respuestas vienen como
# `"token": "eyJ..."`, mientras que la API de Firebase devuelve JSON compacto
# (`"idToken":"..."`). Un patrón que solo aceptara una de las dos formas
# devolvería vacío contra la otra.
campo() {
    printf '%s' "$2" | tr ',' '\n' |
        grep -oE "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 |
        sed "s/^\"$1\"[[:space:]]*:[[:space:]]*\"//; s/\"$//"
}

# curl que devuelve el cuerpo y, en la última línea, el código HTTP.
# El `|| true` evita que `set -e` mate el script ante un fallo de red: curl
# escribe igualmente el código (000) y así el error se reporta con contexto en
# lugar de terminar en silencio.
peticion() {
    "$CURL" -sS -m 30 -w '\n%{http_code}' "$@" 2>&1 || true
}

cuerpo_de()  { printf '%s' "$1" | sed '$d'; }
codigo_de()  { printf '%s' "$1" | tail -1; }

exigir_variables() {
    faltan=""
    for v in "$@"; do
        eval "valor=\${$v:-}"
        [ -n "$valor" ] || faltan="$faltan $v"
    done
    if [ -n "$faltan" ]; then
        printf '%sFaltan variables de entorno:%s%s\n' "$rojo" "$fin" "$faltan" >&2
        printf 'Mira la cabecera de este archivo para el uso.\n' >&2
        exit 2
    fi
}

# ---------------------------------------------------------------------------
# Modo --verificar: solo comprueba que una convocatoria sigue existiendo.
# Se corre DESPUÉS de reiniciar el servicio en Railway; es lo que demuestra que
# el arranque ya no borra la base de datos (el fix de la Fase 1).
# ---------------------------------------------------------------------------
if [ "${1:-}" = "--verificar" ]; then
    exigir_variables BASE_URL
    id="${2:-}"
    [ -n "$id" ] || { echo "Uso: $0 --verificar <id-de-convocatoria>" >&2; exit 2; }

    r=$(peticion "$BASE_URL/api/v1/convocatories/$id")
    if [ "$(codigo_de "$r")" = "200" ]; then
        ok "La convocatoria $id sobrevivió al reinicio."
        info "Los datos persisten: el arranque ya no borra la base."
        exit 0
    fi
    fallo "La convocatoria $id ya no existe (HTTP $(codigo_de "$r"))."
    info "Si el servicio se reinició y el dato desapareció, el arranque está borrando datos."
    exit 1
fi

exigir_variables BASE_URL FIREBASE_API_KEY SMOKE_EMAIL SMOKE_PASSWORD

printf '\nSmoke test contra %s\n\n' "$BASE_URL"

# ---------------------------------------------------------------------------
# 1. El proceso está vivo
# ---------------------------------------------------------------------------
r=$(peticion "$BASE_URL/health")
if [ "$(codigo_de "$r")" = "200" ]; then
    ok "/health responde 200."
else
    fallo "/health devolvió $(codigo_de "$r"): $(cuerpo_de "$r")"
    printf '\nSin healthcheck no tiene sentido seguir.\n'
    exit 1
fi

# ---------------------------------------------------------------------------
# 2. La verificación de tokens de Firebase es REAL
#
# La comprobación más importante del script, y la más fácil de escribir mal.
#
# Mandar un Bearer basura a un endpoint protegido NO detecta el modo mock: esos
# endpoints validan el JWT que emite este backend, no el token de Firebase, así
# que responden 401 ante cualquier cadena inválida en los dos modos. Es una
# prueba que pasa siempre y no demuestra nada. (Comprobado contra el backend
# levantado en modo mock: /auth/me con basura devuelve 401 igualmente.)
#
# Lo que sí separa los dos modos es /auth/login, que es donde se verifica el
# token de Firebase. En modo mock acepta firebaseToken="token-inventado",
# responde 200 y emite un JWT válido para un usuario recién creado — verificado
# en local. En modo firebase eso tiene que ser 401.
# ---------------------------------------------------------------------------
r=$(peticion -X POST -H 'Content-Type: application/json' \
    -d '{"firebaseToken":"token-inventado"}' \
    "$BASE_URL/api/v1/auth/login")
case "$(codigo_de "$r")" in
    401) ok "/auth/login rechaza un token de Firebase inventado. La verificación es real." ;;
    200) fallo "¡PARA! /auth/login aceptó un token INVENTADO y emitió un JWT."
         info "El backend está en modo mock: cualquiera entra como quien quiera."
         info "Pon FIREBASE_AUTH_MODE=firebase en Railway. No generes ningún release así." ;;
    *)   fallo "Con un token de Firebase falso se esperaba 401 y llegó $(codigo_de "$r")." ;;
esac

# Y que la capa del JWT propio también rechace basura.
r=$(peticion -H 'Authorization: Bearer no-es-un-jwt' "$BASE_URL/api/v1/auth/me")
if [ "$(codigo_de "$r")" = "401" ]; then
    ok "Los endpoints protegidos rechazan un JWT inválido."
else
    fallo "/auth/me con un JWT basura devolvió $(codigo_de "$r") en vez de 401."
fi

# ---------------------------------------------------------------------------
# 3. Token real de Firebase (email/contraseña, vía la API REST de Identity)
# ---------------------------------------------------------------------------
r=$(peticion -X POST \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\",\"returnSecureToken\":true}" \
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$FIREBASE_API_KEY")

id_token=$(campo idToken "$(cuerpo_de "$r")")
if [ -n "$id_token" ]; then
    ok "Firebase emitió un ID token para $SMOKE_EMAIL."
else
    fallo "Firebase no devolvió idToken (HTTP $(codigo_de "$r"))."
    info "$(cuerpo_de "$r" | head -c 300)"
    info "Revisa FIREBASE_API_KEY, y que el usuario exista con proveedor email/contraseña."
    exit 1
fi

# ---------------------------------------------------------------------------
# 4. Login: Firebase → JWT propio
# ---------------------------------------------------------------------------
r=$(peticion -X POST \
    -H 'Content-Type: application/json' \
    -d "{\"firebaseToken\":\"$id_token\"}" \
    "$BASE_URL/api/v1/auth/login")

jwt=$(campo token "$(cuerpo_de "$r")")
if [ "$(codigo_de "$r")" = "200" ] && [ -n "$jwt" ]; then
    ok "/auth/login aceptó el token de Firebase y emitió un JWT."
else
    fallo "/auth/login devolvió $(codigo_de "$r")."
    info "$(cuerpo_de "$r" | head -c 300)"
    printf '\nSin JWT no se puede seguir.\n'
    exit 1
fi

auth="Authorization: Bearer $jwt"

# ---------------------------------------------------------------------------
# 5. El JWT sirve para pedir datos propios
# ---------------------------------------------------------------------------
r=$(peticion -H "$auth" "$BASE_URL/api/v1/auth/me")
if [ "$(codigo_de "$r")" = "200" ]; then
    ok "/auth/me responde con el JWT propio."
else
    fallo "/auth/me devolvió $(codigo_de "$r"): $(cuerpo_de "$r" | head -c 200)"
fi

# ---------------------------------------------------------------------------
# 6. Crear convocatoria — escribe en la base y publica el pin
#
# scheduledAt va en ISO local SIN zona ni sufijo Z: el backend lo lee con
# LocalDateTime.parse y un "Z" al final hace que falle silenciosamente.
# ---------------------------------------------------------------------------
# GNU (Git Bash/Linux) y BSD (macOS) no comparten la sintaxis de `date`.
cuando=$( { date -u -d '+2 days' '+%Y-%m-%dT20:00:00' 2>/dev/null ||
            date -u -v+2d    '+%Y-%m-%dT20:00:00' 2>/dev/null; } || true )
if [ -z "$cuando" ]; then
    aviso "No pude calcular una fecha futura con 'date'; uso una fija."
    cuando="2027-01-15T20:00:00"
fi

r=$(peticion -X POST -H "$auth" -H 'Content-Type: application/json' \
    -d "{\"lat\":$LAT,\"lng\":$LNG,\"addressText\":\"Smoke test — borrar\",
         \"positionSlots\":[{\"position\":\"Delantero\",\"slots\":2}],
         \"fee\":0.0,\"format\":\"Fútbol 5\",\"ambiente\":\"Recocha\",
         \"scheduledAt\":\"$cuando\"}" \
    "$BASE_URL/api/v1/convocatories")

convocatoria_id=$(campo id "$(cuerpo_de "$r")")
if [ "$(codigo_de "$r")" = "201" ] && [ -n "$convocatoria_id" ]; then
    ok "Convocatoria creada ($convocatoria_id)."
else
    fallo "Crear convocatoria devolvió $(codigo_de "$r")."
    info "$(cuerpo_de "$r" | head -c 300)"
fi

# ---------------------------------------------------------------------------
# 7. Búsqueda por cercanía — esto es lo que ejerce PostGIS de verdad
#
# Sin token a propósito: /nearby oculta al organizador sus propias
# convocatorias, así que autenticado NO vería el pin que acaba de crear y el
# test daría un falso negativo.
# ---------------------------------------------------------------------------
if [ -n "$convocatoria_id" ]; then
    r=$(peticion "$BASE_URL/api/v1/convocatories/nearby?lat=$LAT&lng=$LNG&radius=2000")
    if [ "$(codigo_de "$r")" != "200" ]; then
        fallo "/nearby devolvió $(codigo_de "$r"): $(cuerpo_de "$r" | head -c 200)"
    elif printf '%s' "$(cuerpo_de "$r")" | grep -q "$convocatoria_id"; then
        ok "/nearby encuentra el pin. ST_DWithin y PostGIS funcionan."
    else
        fallo "/nearby respondió 200 pero no trae la convocatoria recién creada."
        info "Sospecha de la extensión PostGIS o del índice GIST."
    fi
fi

# ---------------------------------------------------------------------------
# 8. WebSocket del mapa — handshake
#
# curl no habla WebSocket, pero sí puede pedir el upgrade: un 101 demuestra que
# la ruta existe y que el proxy TLS de Railway no rompe la conexión, que es lo
# que suele fallar al pasar de local a producción.
#
# Tras el 101 curl deja la conexión abierta y siempre acaba en timeout (error
# 28). Es lo esperado: el código ya se imprimió. Por eso el stderr va a
# /dev/null — si se mezclara con stdout, `r` valdría "101curl: (28)..." y la
# comparación fallaría aunque el handshake hubiera ido bien.
# ---------------------------------------------------------------------------
r=$("$CURL" -sS -m 8 -o /dev/null -w '%{http_code}' \
    --http1.1 \
    -H 'Connection: Upgrade' \
    -H 'Upgrade: websocket' \
    -H 'Sec-WebSocket-Version: 13' \
    -H 'Sec-WebSocket-Key: c21va2UtdGVzdC0xMjM0NQ==' \
    "$BASE_URL/ws/map?lat=$LAT&lng=$LNG" 2>/dev/null || true)

if [ "$r" = "101" ]; then
    ok "/ws/map acepta el upgrade a WebSocket (101)."
else
    fallo "/ws/map no hizo el upgrade (devolvió $r)."
    info "Comprueba que Railway no esté cortando conexiones largas."
fi

# ---------------------------------------------------------------------------
# Resumen y siguiente paso manual
# ---------------------------------------------------------------------------
printf '\n'
if [ -n "$convocatoria_id" ]; then
    printf 'Convocatoria de prueba: %s\n\n' "$convocatoria_id"
    printf 'Falta la prueba de persistencia, que no se puede automatizar desde aquí:\n'
    printf '  1. Reinicia el servicio backend en Railway.\n'
    printf '  2. Cuando vuelva a estar arriba, corre:\n'
    printf '       ./scripts/smoke-test.sh --verificar %s\n' "$convocatoria_id"
    printf '  3. Luego bórrala desde la app, o con:\n'
    printf '       curl -X DELETE -H "Authorization: Bearer <jwt>" \\\n'
    printf '            -H "Content-Type: application/json" -d %s \\\n' "'{\"reason\":\"Otro\"}'"
    printf '            %s/api/v1/convocatories/%s\n\n' "$BASE_URL" "$convocatoria_id"
fi

printf 'Y en un dispositivo real, a mano: confirmar que llega un push FCM.\n\n'

if [ "$fallos" -eq 0 ]; then
    printf '%sTodas las comprobaciones automáticas pasaron.%s\n\n' "$verde" "$fin"
    exit 0
fi
printf '%s%s comprobación(es) fallaron.%s\n\n' "$rojo" "$fallos" "$fin"
exit 1
