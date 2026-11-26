#!/usr/bin/env bash
#
# Resuelve de qué rama parte una corrida de la factory: la de `main` por
# defecto, o la que el issue declare para apilarse sobre otra feature.
#
# Uso:  rama-base.sh <issue>      imprime el nombre de la rama (solo eso) en stdout
# Pide: GH_TOKEN y GITHUB_REPOSITORY (los pone el workflow). No necesita checkout:
#       lee el issue y comprueba la rama con la API, así que corre antes de
#       `actions/checkout`.
#
# Cómo se declara, en el cuerpo del issue (gana la primera que traiga valor):
#   1. el campo "Rama base (opcional)" de la plantilla factory-feature.yml, que
#      GitHub renderiza como `### Rama base (opcional)` + valor (o `_No response_`);
#   2. una línea suelta `Base: <rama>`, para issues que ya existen.
# Sin nada, o vacío → `main`.
#
# Si la rama no es válida o no existe, el script comenta en el issue, escribe el
# motivo en stderr y sale con 1: el job termina antes de correr el agente.
#
# Las funciones se pueden `source`ar (ver rama-base.test.sh).

set -euo pipefail

TITULO_CAMPO='Rama base (opcional)'

# Quita \r, espacios y las comillas inversas con que se suele escribir una rama.
limpiar() {
  local v="${1//$'\r'/}"
  v="${v#"${v%%[![:space:]]*}"}"; v="${v%"${v##*[![:space:]]}"}"
  v="${v#\`}"; v="${v%\`}"
  printf '%s' "$v"
}

# extraer_base <cuerpo>: imprime la rama declarada, o nada si no hay.
extraer_base() {
  local cuerpo="${1//$'\r'/}" linea valor="" en_campo=false
  while IFS= read -r linea; do
    if [ "$en_campo" = true ]; then
      [ -n "$(limpiar "$linea")" ] || continue  # salta líneas vacías tras el título
      valor="$(limpiar "$linea")"
      break
    fi
    [ "$(limpiar "$linea")" = "### $TITULO_CAMPO" ] && en_campo=true
  done <<< "$cuerpo"
  # Un campo vacío de un issue form se renderiza así.
  [ "$valor" = "_No response_" ] && valor=""
  # Otro título de sección justo después significa que el campo venía sin valor.
  case "$valor" in "### "*) valor="" ;; esac

  if [ -z "$valor" ]; then
    while IFS= read -r linea; do
      linea="$(limpiar "$linea")"
      if [[ "$linea" =~ ^[Bb]ase:[[:space:]]*(.*)$ ]]; then
        valor="$(limpiar "${BASH_REMATCH[1]}")"
        [ -n "$valor" ] && break
      fi
    done <<< "$cuerpo"
  fi
  printf '%s' "$valor"
}

# nombre_valido <rama>: solo caracteres seguros, sin `..`, sin empezar por `-` o `/`.
nombre_valido() {
  local r="$1"
  [ -n "$r" ] || return 1
  [[ "$r" =~ ^[A-Za-z0-9._/-]+$ ]] || return 1
  [[ "$r" != *..* ]] || return 1
  [[ "$r" != -* && "$r" != /* ]] || return 1
}

# rama_existe <rama>: la API, no `git ls-remote`, porque aún no hay checkout ni credenciales.
rama_existe() {
  gh api "repos/$GITHUB_REPOSITORY/branches/$1" --silent >/dev/null 2>&1
}

rechazar() { # rechazar <issue> <motivo>
  local issue="$1" motivo="$2"
  echo "::error title=Rama base inválida::$motivo" >&2
  printf '<!-- factory:estado -->\n❌ No arranqué: %s\n\nCorrige el campo "Rama base (opcional)" (o la línea `Base: …`) del issue y vuelve a poner la etiqueta.\n' "$motivo" \
    | gh issue comment "$issue" --repo "$GITHUB_REPOSITORY" --body-file - >/dev/null || true
  exit 1
}

principal() {
  local issue="${1:?Uso: rama-base.sh <número de issue>}"
  : "${GITHUB_REPOSITORY:?Falta GITHUB_REPOSITORY}"
  local cuerpo rama
  cuerpo="$(gh issue view "$issue" --repo "$GITHUB_REPOSITORY" --json body --jq '.body // ""')"
  rama="$(extraer_base "$cuerpo")"
  if [ -z "$rama" ]; then echo main; return 0; fi
  nombre_valido "$rama" \
    || rechazar "$issue" "el nombre de la rama base no es válido (solo letras, números, \`.\`, \`_\`, \`-\` y \`/\`, sin \`..\`)"
  if [ "$rama" != main ]; then
    rama_existe "$rama" || rechazar "$issue" "la rama base \`$rama\` no existe en el repositorio"
  fi
  printf '%s\n' "$rama"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then principal "$@"; fi
