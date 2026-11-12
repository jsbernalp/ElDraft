#!/usr/bin/env bash
#
# Hook PostToolUse de Claude Code (Edit / Write / MultiEdit).
#
# Corre el linter de Design Tokens SOLO sobre el archivo que se acaba de editar,
# y solo si es un .kt de la UI de Compose. Así el agente se entera del color o
# radio quemado en el mismo turno en que lo escribió, en vez de descubrirlo en el
# pre-commit cuando ya hay diez archivos más encima.
#
# Claude Code le pasa por stdin un JSON con `tool_input.file_path`. Con exit 2 lo
# que se imprima en stderr le llega al modelo como feedback; con exit 0 no pasa
# nada. Nunca bloquea por un fallo propio (sin jq, JSON raro…): un hook que
# rompe la edición es peor que uno que se calla.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK="$SCRIPT_DIR/../check-design-tokens.sh"

command -v jq >/dev/null 2>&1 || exit 0
[ -x "$CHECK" ] || exit 0

file="$(jq -r '.tool_input.file_path // empty' 2>/dev/null)"
[ -n "$file" ] || exit 0

# Mismo filtro que el linter: UI de Compose, fuera de theme/ (ahí viven los literales).
case "$file" in
  *com/eldraft/android/ui/theme/*) exit 0 ;;
  *androidApp/src/main/kotlin/com/eldraft/android/ui/*.kt) ;;
  *) exit 0 ;;
esac

if ! output="$("$CHECK" "$file" 2>&1)"; then
  {
    echo "$output"
    echo ""
    echo "Corrige con los tokens de ui/theme/ (ver CLAUDE.md) o marca el one-off"
    echo "legítimo con '// design-tokens-ignore: <razón>'."
  } >&2
  exit 2
fi
exit 0
