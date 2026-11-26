#!/usr/bin/env bash
#
# Puertas deterministas de la factory en CI: lo que el agente dice que pasó no
# cuenta, aquí se vuelve a correr. Las mismas suites que Backend CI e iOS CI,
# para que un PR de la factory llegue ya sabiendo si está en verde, también
# cuando se abrió con GITHUB_TOKEN y GitHub no le corre el CI.
#
# Uso, desde elDraft-app/:
#   ./scripts/factory/puertas.sh android   # backend, shared, ViewModels y tokens
#   ./scripts/factory/puertas.sh ios       # shared en Kotlin/Native + build de iOS
#
# Escribe el resumen en $FACTORY_OUT/puertas.md (lo pega publicar.sh en el PR).
# Sale con 0 si todo está en verde, 1 si algo falló. Si el agente no dejó
# cambios, no corre nada: no hay qué verificar.

set -uo pipefail

PERFIL="${1:?Uso: puertas.sh android|ios}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$(cd "$DIR/../.." && pwd)"
OUT="${FACTORY_OUT:-${RUNNER_TEMP:-/tmp}/factory}"
RESUMEN="$OUT/puertas.md"
mkdir -p "$OUT"
cd "$APP" || exit 1

# Con base apilada el workflow superpone las herramientas de main sobre el
# checkout: eso no cuenta como cambio del agente (ver publicar.sh).
EXCLUIR=()
[ "${FACTORY_BASE:-main}" = main ] || EXCLUIR=(':(exclude)scripts/factory')
if [ -z "$(git status --porcelain -- . ${EXCLUIR[@]+"${EXCLUIR[@]}"})" ]; then
  echo "Sin cambios: no hay puertas que correr." | tee "$RESUMEN"
  exit 0
fi

: > "$RESUMEN"
fallos=0

# correr "<nombre>" <comando…>
correr() {
  local nombre="$1"; shift
  local log
  log="$OUT/puerta-$(echo "$nombre" | tr -cs '[:alnum:]' '-').log"
  echo "::group::$nombre"
  if "$@" > "$log" 2>&1; then
    tail -5 "$log"
    echo "- ✅ $nombre" >> "$RESUMEN"
  else
    tail -60 "$log"
    echo "::error title=Puerta en rojo::$nombre"
    {
      echo "- ❌ **$nombre**"
      echo ""
      echo '  ```'
      grep -E 'FAILED|error:|e: |Exception|✘|violaci' "$log" | head -15 | sed 's/^/  /'
      echo '  ```'
    } >> "$RESUMEN"
    fallos=$((fallos + 1))
  fi
  echo "::endgroup::"
}

gradle() { ./gradlew --no-daemon --console=plain "$@"; }

case "$PERFIL" in
  android)
    # -PbackendOnly igual que en backend.yml: el backend no necesita el SDK.
    correr "Tests del backend"            gradle -PbackendOnly :backend:test
    correr "Tests de :shared"             gradle :shared:testDebugUnitTest
    correr "Tests de ViewModels Android"  gradle :androidApp:testDebugUnitTest
    correr "Design tokens"                ./scripts/check-design-tokens.sh
    ;;
  ios)
    [ -f iosApp/Config/Debug.xcconfig ] || cp iosApp/Config/Debug.xcconfig.example iosApp/Config/Debug.xcconfig
    # Los tests de :shared en la JVM no van aquí: en macOS dependerían del SDK
    # de Android, y Backend CI ya los corre en el PR.
    correr "Tests de :shared (Kotlin/Native)" gradle :shared:iosSimulatorArm64Test
    # shellcheck disable=SC2016  # $RUNNER_TEMP lo expande el bash de adentro.
    correr "Build de iOS (sin firmar)" bash -c '
      cd iosApp && xcodebuild build-for-testing \
        -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
        -destination "generic/platform=iOS Simulator" \
        -derivedDataPath "${RUNNER_TEMP:-/tmp}/DerivedData" \
        CODE_SIGNING_ALLOWED=NO'
    # check-paridad informa pero no bloquea: lo aprobado puede ser solo una
    # parte de los huecos (p. ej. la prioridad 1), y el resto sigue saliendo.
    echo "::group::check-paridad (informativo)"
    {
      echo ""
      echo "check-paridad después del port (informativo, no bloquea):"
      echo '```'
      ./scripts/check-paridad.sh --resumen 2>&1 | tail -25
      echo '```'
    } >> "$RESUMEN"
    echo "::endgroup::"
    ;;
  *)
    echo "Perfil desconocido: $PERFIL" >&2
    exit 64 ;;
esac

cat "$RESUMEN"
[ "$fallos" -eq 0 ]
