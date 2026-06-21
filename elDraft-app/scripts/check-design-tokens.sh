#!/usr/bin/env bash
#
# Verifica que la UI de Compose use los Design Tokens (ElDraftTheme.*) en vez de
# valores quemados. Falla si encuentra colores, opacidades o radios literales en
# las capas de UI (screens / map / components). La definición de los tokens vive
# en ui/theme/, que queda excluida.
#
# Uso:
#   ./scripts/check-design-tokens.sh            # revisa todos los archivos UI
#   ./scripts/check-design-tokens.sh f1.kt f2…  # revisa solo esos archivos (para el hook)
#
# Salida: 0 si todo limpio; 1 si hay violaciones (las imprime con archivo:línea).

set -uo pipefail

# Raíz de la UI de Compose (relativa a la ubicación del script).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UI_DIR="$SCRIPT_DIR/../androidApp/src/main/kotlin/com/eldraft/android/ui"

# Archivos a revisar: los pasados por argumento (filtrados a la UI, fuera de theme/)
# o, si no hay argumentos, todos los .kt de screens/map/components.
FILES=()
if [ "$#" -gt 0 ]; then
  for f in "$@"; do
    case "$f" in
      *com/eldraft/android/ui/theme/*) ;;                 # los tokens viven aquí: excluir
      *com/eldraft/android/ui/*.kt) [ -f "$f" ] && FILES+=("$f") ;;
    esac
  done
else
  while IFS= read -r f; do
    FILES+=("$f")
  done < <(find "$UI_DIR/screens" "$UI_DIR/map" "$UI_DIR/components" -name "*.kt" 2>/dev/null)
fi

# Nada que revisar (p. ej. el commit no toca archivos de UI).
[ "${#FILES[@]}" -eq 0 ] && { echo "✓ Sin archivos de UI que revisar."; exit 0; }

# Reglas: descripción => patrón grep -E. Cada match es una violación.
# Nota: las dimensiones .dp/.sp NO se vigilan aquí porque hay one-off legítimos
# (dibujo de pines, avatares, layout fullscreen) que darían falsos positivos.
# El foco es color / opacidad / forma, donde un literal casi siempre es un error.
declare -a RULES=(
  "color hardcodeado (usa ElDraftTheme.colors.* o MaterialTheme.colorScheme.*)|Color\(0x"
  "opacidad mágica (usa ElDraftTheme.alpha.*)|copy\(alpha = [0-9]"
  "radio literal (usa ElDraftTheme.shape.*)|RoundedCornerShape\([0-9]"
)

# Una línea con el comentario `design-tokens-ignore` se considera una excepción
# consciente (one-off legítimo: dibujo en Canvas, scrim, etc.) y no se reporta.
IGNORE_MARK="design-tokens-ignore"

violations=0
for rule in "${RULES[@]}"; do
  desc="${rule%%|*}"
  pat="${rule##*|}"
  for f in "${FILES[@]}"; do
    while IFS= read -r match; do
      [ -z "$match" ] && continue
      lineno="${match%%:*}"
      content="${match#*:}"
      # Saltar las líneas marcadas como excepción consciente.
      case "$content" in *"$IGNORE_MARK"*) continue ;; esac
      rel="${f##*/com/eldraft/android/ui/}"
      echo "  ✗ $desc"
      echo "    ui/$rel:$lineno"
      violations=$((violations + 1))
    done < <(grep -nE "$pat" "$f" 2>/dev/null)
  done
done

if [ "$violations" -gt 0 ]; then
  echo ""
  echo "✗ $violations valor(es) quemado(s) en la UI. Usa los Design Tokens de ui/theme/:"
  echo "    color → ElDraftTheme.colors.*   opacidad → ElDraftTheme.alpha.*"
  echo "    radio → ElDraftTheme.shape.*    spacing  → ElDraftTheme.spacing.*"
  echo "    tamaño → ElDraftTheme.size.*    elevación→ ElDraftTheme.elevation.*"
  echo "  Si es un caso one-off legítimo (dibujo en Canvas, etc.), documéntalo con un comentario."
  exit 1
fi

echo "✓ Design tokens OK: sin colores, opacidades ni radios quemados en la UI."
exit 0
