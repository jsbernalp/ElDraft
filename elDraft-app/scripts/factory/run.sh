#!/usr/bin/env bash
#
# Corre una etapa de la factory de elDraft en modo CI (claude -p, sin humano).
#
# Uso, desde elDraft-app/:
#   ./scripts/factory/run.sh <etapa> [contexto.md]
#
# Etapas:
#   plan-feature       planificador, solo lectura            → comentario en el issue
#   auditar-paridad    auditor-paridad, solo lectura         → comentario en el issue
#   construir-feature  pasos 2-3 de /feature (Ubuntu)        → cambios en el árbol
#   construir-paridad  pasos 3-4 de /paridad (macOS)         → cambios en el árbol
#   mantenimiento      /mantenimiento $FACTORY_MODO          → cambios en el árbol
#
# Corre DESDE elDraft-app a propósito: así Claude Code carga el CLAUDE.md, las
# skills, los agentes y el settings.json del proyecto (con sus negaciones y el
# hook de tokens), igual que en una sesión local. Lo único que cambia es lo
# que explica prompts/_sistema-ci.md.
#
# Deja en $FACTORY_OUT (por defecto $RUNNER_TEMP/factory):
#   prompt-<etapa>.md   el prompt exacto que recibió el agente
#   resultado.json      la salida de `claude -p --output-format json`; lo que
#                       importa está en .structured_output (ver schemas/)
#
# Variables opcionales:
#   FACTORY_ISSUE, FACTORY_MODO, FACTORY_DETALLE   rellenan el prompt
#   FACTORY_MODEL            modelo del orquestador (los subagentes usan el suyo)
#   FACTORY_MAX_BUDGET_USD   tope de gasto estimado de la corrida
#   FACTORY_MAX_TURNS        tope de turnos del orquestador
#
# El git es del workflow, no del agente: aquí se le niegan commit, push y
# cambios de rama, y el checkout no deja el token en .git/config. Las reglas de
# Bash no son una frontera de seguridad (un `sh -c` las esquiva); la frontera es
# que el runner es efímero, el token de GitHub no está en el entorno de este
# paso y el push lo hace publicar.sh después.

set -euo pipefail

ETAPA="${1:?Uso: run.sh <etapa> [contexto.md]}"
CONTEXTO="${2:-}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$(cd "$DIR/../.." && pwd)"
OUT="${FACTORY_OUT:-${RUNNER_TEMP:-/tmp}/factory}"
mkdir -p "$OUT"
cd "$APP"

# --- Credenciales: una de las dos. Una variable vacía (secret sin definir) se
# quita del entorno para que Claude Code no intente usarla. En local (sin $CI)
# vale la sesión que ya tengas iniciada.
[ -n "${CLAUDE_CODE_OAUTH_TOKEN:-}" ] || unset CLAUDE_CODE_OAUTH_TOKEN
[ -n "${ANTHROPIC_API_KEY:-}" ] || unset ANTHROPIC_API_KEY
if [ -n "${CI:-}" ] && [ -z "${CLAUDE_CODE_OAUTH_TOKEN:-}" ] && [ -z "${ANTHROPIC_API_KEY:-}" ]; then
  echo "::error title=Falta la credencial de Claude::Define el secret CLAUDE_CODE_OAUTH_TOKEN (claude setup-token) o ANTHROPIC_API_KEY. Ver scripts/factory/README.md." >&2
  exit 78
fi

# --- Perfil de cada etapa.
case "$ETAPA" in
  plan-feature|auditar-paridad)
    PERFIL=lectura;  SCHEMA=plan;          MODELO=opus;   TURNOS=80;  PRESUPUESTO=10 ;;
  construir-feature)
    PERFIL=android;  SCHEMA=construccion;  MODELO=opus;   TURNOS=250; PRESUPUESTO=60 ;;
  construir-paridad)
    PERFIL=ios;      SCHEMA=construccion;  MODELO=opus;   TURNOS=250; PRESUPUESTO=60 ;;
  mantenimiento)
    PERFIL=android;  SCHEMA=construccion;  MODELO=sonnet; TURNOS=150; PRESUPUESTO=25 ;;
  *)
    echo "Etapa desconocida: $ETAPA" >&2
    exit 64 ;;
esac
MODELO="${FACTORY_MODEL:-$MODELO}"
TURNOS="${FACTORY_MAX_TURNS:-$TURNOS}"
PRESUPUESTO="${FACTORY_MAX_BUDGET_USD:-$PRESUPUESTO}"

# --- Herramientas. Todo lo que no esté aquí se deniega (no hay nadie a quien
# preguntar). Los comandos de solo lectura (ls, cat, grep, git log/diff/show…)
# no necesitan regla: Claude Code ya los deja pasar.
PERMITIDOS=(
  Read Glob Grep Skill Agent
  "Bash(./scripts/check-paridad.sh *)"
  "Bash(./scripts/check-design-tokens.sh *)"
)
NEGADOS=(
  "Bash(git push *)" "Bash(git commit *)" "Bash(git checkout *)" "Bash(git switch *)"
  "Bash(git reset *)" "Bash(git rebase *)" "Bash(git merge *)" "Bash(git remote *)"
  "Bash(git config *)" "Bash(gh *)" "Bash(curl *)" "Bash(wget *)"
  "Bash(env *)" "Bash(printenv *)"
)
case "$PERFIL" in
  lectura)
    MODO_PERMISOS=dontAsk
    NEGADOS+=(Edit Write NotebookEdit)
    ;;
  android|ios)
    MODO_PERMISOS=acceptEdits
    PERMITIDOS+=(
      Edit Write
      "Bash(./gradlew *)" "Bash(./scripts/*)" "Bash(chmod +x *)"
      "Bash(git add *)" "Bash(git rm *)" "Bash(git mv *)" "Bash(git restore *)"
      "Bash(git stash *)"
      # actualizador-deps lee changelogs y notas de versión.
      WebFetch WebSearch
    )
    if [ "$PERFIL" = ios ]; then
      PERMITIDOS+=(
        "Bash(xcodegen *)" "Bash(xcodebuild *)" "Bash(xcrun *)"
        "Bash(brew install xcodegen)" "Bash(plutil *)"
      )
    fi
    ;;
esac

unir() { local IFS=,; echo "$*"; }

# --- Prompt: plantilla de la etapa con {{VARIABLES}} + contexto del issue.
export FACTORY_ISSUE="${FACTORY_ISSUE:-}"
FACTORY_MODO="${FACTORY_MODO:-}"
export FACTORY_MODO="${FACTORY_MODO#factory:}"
export FACTORY_DETALLE="${FACTORY_DETALLE:-}"
if [ -n "$FACTORY_ISSUE" ]; then
  export FACTORY_ORIGEN="El pedido viene del issue #$FACTORY_ISSUE, que va al final de este mensaje."
else
  export FACTORY_ORIGEN="Corrida programada, sin issue."
fi

PROMPT="$OUT/prompt-$ETAPA.md"
python3 -I - "$DIR/prompts/$ETAPA.md" "$CONTEXTO" "$PROMPT" <<'PY'
import os, re, sys

plantilla, contexto, salida = sys.argv[1:4]
with open(plantilla, encoding="utf-8") as f:
    texto = f.read()
texto = re.sub(r"\{\{([A-Z_]+)\}\}",
               lambda m: os.environ.get("FACTORY_" + m.group(1), ""), texto)
if contexto:
    with open(contexto, encoding="utf-8") as f:
        texto += "\n\n---\n\n" + f.read()
with open(salida, "w", encoding="utf-8") as f:
    f.write(texto)
PY

echo "::group::Prompt de $ETAPA"
cat "$PROMPT"
echo "::endgroup::"

# --- Corrida. El prompt va por stdin: las opciones de lista (--allowedTools…)
# se tragarían un argumento posicional que viniera después de ellas.
set +e
claude -p \
  --model "$MODELO" \
  --permission-mode "$MODO_PERMISOS" \
  --permission-prompts none \
  --allowedTools "$(unir "${PERMITIDOS[@]}")" \
  --disallowedTools "$(unir "${NEGADOS[@]}")" \
  --append-system-prompt-file "$DIR/prompts/_sistema-ci.md" \
  --json-schema "$(cat "$DIR/schemas/$SCHEMA.json")" \
  --max-turns "$TURNOS" \
  --max-budget-usd "$PRESUPUESTO" \
  --output-format json \
  < "$PROMPT" > "$OUT/resultado.json"
codigo=$?
set -e

# --- Resumen para el log. El veredicto lo da publicar.sh, no este script.
if jq -e . "$OUT/resultado.json" >/dev/null 2>&1; then
  jq -r '"subtipo: \(.subtype // "?") · error: \(.is_error | tostring) · turnos: \(.num_turns // "?") · costo estimado: US$ \(.total_cost_usd // "?")"' "$OUT/resultado.json"
  jq -r '.structured_output.estado // "sin estado estructurado"' "$OUT/resultado.json"
else
  echo "La salida de claude no es JSON:" >&2
  head -c 4000 "$OUT/resultado.json" >&2 || true
fi
exit "$codigo"
