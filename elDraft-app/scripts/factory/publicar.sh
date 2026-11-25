#!/usr/bin/env bash
#
# Publica en GitHub lo que hizo una corrida de la factory: el aviso de inicio,
# el plan o el informe en el issue, las etiquetas de estado y —si el agente dejó
# cambios— la rama, el commit y el PR.
#
# Uso, desde la raíz del repo (lo llaman los workflows):
#   elDraft-app/scripts/factory/publicar.sh inicio
#   elDraft-app/scripts/factory/publicar.sh resultado
#
# Variables:
#   FACTORY_ETAPA, FACTORY_ISSUE (vacío en corridas programadas), FACTORY_MODO
#   AGENTE_OUTCOME, PUERTAS_OUTCOME   outcome de esos pasos (success/failure/skipped)
#   GH_TOKEN      GITHUB_TOKEN: comentarios y etiquetas del issue
#   PUSH_TOKEN    push y PR (FACTORY_GH_TOKEN si existe, si no GITHUB_TOKEN)
#   PUSH_CON_GITHUB_TOKEN  "true" si PUSH_TOKEN es GITHUB_TOKEN
#   GITHUB_*      los del runner
#
# Qué decide
# ----------
#   etapa de plan                 → comentario con <!-- factory:plan --> + factory:plan-listo
#   sin cambios en el árbol       → nota en el issue (o en el resumen del run)
#   cambios, estado "listo" y puertas en verde → PR listo para revisar
#   cambios, cualquier otra cosa  → PR en BORRADOR + factory:bloqueado: el
#                                   trabajo no se pierde y en el PR se puede
#                                   seguir con "@claude …"
#
# El commit lo firma "elDraft factory" con el mensaje que redactó el agente con
# la skill `commit`. Este script nunca empuja a `main`: la rama es siempre nueva.

set -euo pipefail

ACCION="${1:?Uso: publicar.sh inicio|resultado}"
OUT="${FACTORY_OUT:-${RUNNER_TEMP:-/tmp}/factory}"
RES="$OUT/resultado.json"
ETAPA="${FACTORY_ETAPA:?Falta FACTORY_ETAPA}"
ISSUE="${FACTORY_ISSUE:-}"
MODO="${FACTORY_MODO:-}"; MODO="${MODO#factory:}"
ETQ_MODO=""; [ -n "$MODO" ] && ETQ_MODO="factory:$MODO"
REPO="${GITHUB_REPOSITORY:?Falta GITHUB_REPOSITORY}"
RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/$REPO/actions/runs/${GITHUB_RUN_ID:-0}"
RUN_N="${GITHUB_RUN_NUMBER:-0}"
RESUMEN_RUN="${GITHUB_STEP_SUMMARY:-/dev/null}"
mkdir -p "$OUT"

M_ESTADO='<!-- factory:estado -->'
M_PLAN='<!-- factory:plan -->'
M_NOTA='<!-- factory:nota -->'

# --- Ayudas -----------------------------------------------------------------

campo() { jq -r --arg k "$1" '(.structured_output // {})[$k] // empty' "$RES" 2>/dev/null || true; }
meta()  { jq -r --arg k "$1" '.[$k] // empty' "$RES" 2>/dev/null || true; }

# Una llamada por etiqueta: quitar una que no está no debe tumbar las demás.
poner()  { [ -n "$ISSUE" ] || return 0; for e in "$@"; do [ -n "$e" ] || continue; gh issue edit "$ISSUE" --repo "$REPO" --add-label "$e" >/dev/null 2>&1 || echo "No pude poner $e" >&2; done; }
quitar() { [ -n "$ISSUE" ] || return 0; for e in "$@"; do [ -n "$e" ] || continue; gh issue edit "$ISSUE" --repo "$REPO" --remove-label "$e" >/dev/null 2>&1 || true; done; }
comentar() { # comentar <archivo>
  if [ -n "$ISSUE" ]; then
    gh issue comment "$ISSUE" --repo "$REPO" --body-file "$1" >/dev/null
  fi
  { cat "$1"; echo; } >> "$RESUMEN_RUN"
}

nombre_etapa() {
  case "$ETAPA" in
    plan-feature)      echo "el plan de la feature" ;;
    auditar-paridad)   echo "la auditoría de paridad iOS" ;;
    construir-feature) echo "la implementación de la feature" ;;
    construir-paridad) echo "el port a iOS" ;;
    mantenimiento)     echo "el mantenimiento \`$MODO\`" ;;
    *)                 echo "$ETAPA" ;;
  esac
}

pie() {
  local turnos costo
  turnos="$(meta num_turns)"; costo="$(meta total_cost_usd)"
  printf '<sub>Factory de elDraft · corrida [#%s](%s)' "$RUN_N" "$RUN_URL"
  [ -n "$turnos" ] && printf ' · %s turnos' "$turnos"
  [ -n "$costo" ] && printf ' · ~US$ %s estimados' "$(LC_ALL=C printf '%.2f' "$costo" 2>/dev/null || echo "$costo")"
  printf '</sub>\n'
}

motivo_fallo() {
  local sub; sub="$(meta subtype)"
  case "$sub" in
    error_max_turns)  echo "se quedó sin turnos (FACTORY_MAX_TURNS)" ;;
    error_max_budget_usd|*budget*) echo "llegó al tope de gasto (FACTORY_MAX_BUDGET_USD_*)" ;;
    "")
      if [ ! -s "$RES" ]; then echo "el agente no llegó a correr (¿falta la credencial de Claude?)"
      else echo "el agente terminó sin devolver un resultado estructurado"; fi ;;
    *) echo "el agente terminó con \`$sub\`" ;;
  esac
}

fallo() { # fallo <motivo>
  {
    echo "$M_ESTADO"
    echo "❌ No pude completar $(nombre_etapa): $1."
    echo ""
    echo "Los logs y lo que vio el agente están en la [corrida]($RUN_URL) (artifact \`factory-…\`)."
    echo "Para reintentar, quita y vuelve a poner la etiqueta."
    echo ""
    pie
  } > "$OUT/comentario.md"
  comentar "$OUT/comentario.md"
  poner factory:bloqueado
  quitar factory:en-curso factory:aprobado "$ETQ_MODO"
}

# --- inicio -----------------------------------------------------------------

if [ "$ACCION" = inicio ]; then
  quitar factory:bloqueado factory:replanear factory:plan-listo factory:pr-abierto
  poner factory:en-curso
  if [ -n "$ISSUE" ]; then
    printf '%s\n🏭 Empecé %s. [Ver la corrida](%s).\n' "$M_ESTADO" "$(nombre_etapa)" "$RUN_URL" > "$OUT/inicio.md"
    gh issue comment "$ISSUE" --repo "$REPO" --body-file "$OUT/inicio.md" >/dev/null
  fi
  exit 0
fi
[ "$ACCION" = resultado ] || { echo "Acción desconocida: $ACCION" >&2; exit 64; }

ESTADO="$(campo estado)"

# --- Etapas de plan: un comentario en el issue, sin tocar código ------------

if [ "$ETAPA" = plan-feature ] || [ "$ETAPA" = auditar-paridad ]; then
  texto="$(campo comentario)"
  if [ -z "$texto" ]; then
    fallo "$(motivo_fallo)"
    exit 0
  fi
  marca="$M_NOTA"; [ "$ESTADO" = plan_listo ] && marca="$M_PLAN"
  { echo "$marca"; echo "$texto"; echo ""; pie; } > "$OUT/comentario.md"
  comentar "$OUT/comentario.md"
  quitar factory:en-curso
  case "$ESTADO" in
    plan_listo) poner factory:plan-listo ;;
    bloqueado)  poner factory:bloqueado ;;
  esac
  exit 0
fi

# --- Etapas que construyen ----------------------------------------------------

INFORME="$(campo informe)"
PREGUNTA="$(campo pregunta)"

if [ -z "$(git status --porcelain)" ]; then
  if [ -z "$ESTADO" ]; then
    fallo "$(motivo_fallo)"
    exit 0
  fi
  {
    echo "$M_NOTA"
    case "$ESTADO" in
      bloqueado) echo "⏸️ Me detuve antes de cambiar nada en $(nombre_etapa)." ;;
      *)         echo "✅ Terminé $(nombre_etapa) sin cambios que proponer." ;;
    esac
    echo ""
    [ -n "$PREGUNTA" ] && { echo "**Necesito que decidas:** $PREGUNTA"; echo ""; }
    [ -n "$INFORME" ] && { echo "$INFORME"; echo ""; }
    [ "$ESTADO" = bloqueado ] && { echo "Responde en un comentario y vuelve a poner \`factory:aprobado\`."; echo ""; }
    pie
  } > "$OUT/comentario.md"
  comentar "$OUT/comentario.md"
  quitar factory:en-curso factory:aprobado "$ETQ_MODO"
  [ "$ESTADO" = bloqueado ] && poner factory:bloqueado
  exit 0
fi

# Hay cambios: siempre se abre un PR, en borrador si algo no está en verde.
BORRADOR=false
RAZONES=()
if [ "$ESTADO" != listo ]; then
  BORRADOR=true
  case "$ESTADO" in
    bloqueado) RAZONES+=("el agente se detuvo esperando una decisión") ;;
    "")        RAZONES+=("$(motivo_fallo); lo que hay es trabajo parcial") ;;
    *)         RAZONES+=("el agente terminó con estado \`$ESTADO\`") ;;
  esac
fi
if [ "${PUERTAS_OUTCOME:-}" != success ]; then
  BORRADOR=true
  RAZONES+=("las puertas deterministas no quedaron en verde")
fi

FECHA="$(date -u +%Y%m%d)"
if [ -n "$ISSUE" ]; then
  RAMA="factory/issue-$ISSUE-$RUN_N"
else
  RAMA="chore/mantenimiento-$MODO-$FECHA-$RUN_N"
fi

ASUNTO="$(campo commit_asunto)"
CUERPO="$(campo commit_cuerpo)"
if [ -z "$ASUNTO" ]; then
  if [ -n "$ISSUE" ]; then ASUNTO="Avanzar el issue #$ISSUE (trabajo parcial de la factory)"
  else ASUNTO="Avanzar el mantenimiento $MODO (trabajo parcial de la factory)"; fi
fi

{
  echo "$ASUNTO"
  echo ""
  if [ -n "$CUERPO" ]; then echo "$CUERPO"; echo ""; fi
  [ -n "$ISSUE" ] && { echo "Refs #$ISSUE"; echo ""; }
  echo "Co-Authored-By: Claude <noreply@anthropic.com>"
} > "$OUT/commit-msg.txt"

git checkout -q -b "$RAMA"
git add -A
git diff --cached --binary > "$OUT/cambios.patch"
git -c user.name="elDraft factory" \
    -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
    commit -q -F "$OUT/commit-msg.txt"

# El checkout se hizo sin credenciales (persist-credentials: false): el token
# entra solo aquí, como cabecera, y no queda guardado en .git/config.
AUTH="$(printf 'x-access-token:%s' "$PUSH_TOKEN" | base64 | tr -d '\n')"
echo "::add-mask::$AUTH"
git -c http.https://github.com/.extraheader="AUTHORIZATION: basic $AUTH" \
    push -q origin "HEAD:refs/heads/$RAMA"

# --- Cuerpo del PR --------------------------------------------------------------
RAZONES_TXT=""
for r in "${RAZONES[@]+"${RAZONES[@]}"}"; do RAZONES_TXT="${RAZONES_TXT:+$RAZONES_TXT; }$r"; done
{
  if [ "$BORRADOR" = true ]; then
    echo "> [!WARNING]"
    echo "> **Borrador:** $RAZONES_TXT."
    echo "> Puedes seguir aquí mismo con un comentario \`@claude …\`."
    echo ""
  fi
  [ -n "$PREGUNTA" ] && { echo "**Necesito que decidas:** $PREGUNTA"; echo ""; }
  if [ -n "$INFORME" ]; then echo "$INFORME"; else echo "_El agente no dejó informe._"; fi
  echo ""
  echo "## Puertas (las corrió el workflow, no el agente)"
  echo ""
  if [ -s "$OUT/puertas.md" ]; then cat "$OUT/puertas.md"; else echo "_No corrieron._"; fi
  echo ""
  if [ "${PUSH_CON_GITHUB_TOKEN:-false}" = true ]; then
    echo "> [!NOTE]"
    echo "> Este PR lo abrió \`GITHUB_TOKEN\`, así que GitHub no le corre Backend CI ni iOS CI."
    echo "> Lánzalos a mano desde Actions, o configura \`FACTORY_GH_TOKEN\` (ver \`elDraft-app/scripts/factory/README.md\`)."
    echo ""
  fi
  [ -n "$ISSUE" ] && { echo "Closes #$ISSUE"; echo ""; }
  echo "---"
  pie
} > "$OUT/pr.md"

ETIQUETAS=(factory)
case "$ETAPA" in
  construir-feature) ETIQUETAS+=(factory:feature) ;;
  construir-paridad) ETIQUETAS+=(factory:paridad) ;;
  mantenimiento)     ETIQUETAS+=("$ETQ_MODO") ;;
esac

args=(--repo "$REPO" --base main --head "$RAMA" --title "$ASUNTO" --body-file "$OUT/pr.md")
[ "$BORRADOR" = true ] && args+=(--draft)
etq=(); for e in "${ETIQUETAS[@]}"; do etq+=(--label "$e"); done

# Si las etiquetas no existen (no se corrió crear-etiquetas.sh), el PR igual se abre.
if ! PR_URL="$(GH_TOKEN="$PUSH_TOKEN" gh pr create "${args[@]}" "${etq[@]}" 2>"$OUT/pr-error.txt")"; then
  PR_URL="$(GH_TOKEN="$PUSH_TOKEN" gh pr create "${args[@]}")"
fi

# --- Aviso en el issue ----------------------------------------------------------
{
  echo "$M_NOTA"
  if [ "$BORRADOR" = true ]; then
    echo "⏸️ Dejé el trabajo en un PR en borrador: $PR_URL"
    echo ""
    for r in "${RAZONES[@]+"${RAZONES[@]}"}"; do echo "- $r"; done
    echo ""
    [ -n "$PREGUNTA" ] && { echo "**Necesito que decidas:** $PREGUNTA"; echo ""; }
    echo "Sigue en el PR con \`@claude …\`, o responde aquí y vuelve a poner \`factory:aprobado\` para empezar de cero."
  else
    echo "✅ Listo para revisar: $PR_URL"
    echo ""
    echo "Las puertas quedaron en verde. Revisa y mergea cuando quieras; el issue se cierra al mergear."
  fi
  echo ""
  pie
} > "$OUT/comentario.md"
comentar "$OUT/comentario.md"

quitar factory:en-curso factory:aprobado "$ETQ_MODO"
poner factory:pr-abierto
[ "$BORRADOR" = true ] && poner factory:bloqueado
echo "PR: $PR_URL"
