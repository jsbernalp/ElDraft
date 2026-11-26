#!/usr/bin/env bash
#
# Pruebas de rama-base.sh, sin red: el parseo con cuerpos de ejemplo y el script
# completo con un `gh` falso. Se corre a mano:  scripts/factory/rama-base.test.sh
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=rama-base.sh
source "$DIR/rama-base.sh"
set +e  # rama-base.sh activa -e; aquí queremos seguir tras un fallo

fallos=0
ok()   { printf '  ok    %s\n' "$1"; }
mal()  { printf '  FALLA %s\n        esperado: %q\n        obtenido: %q\n' "$1" "$2" "$3"; fallos=$((fallos+1)); }
igual() { [ "$3" = "$2" ] && ok "$1" || mal "$1" "$2" "$3"; }

echo "extraer_base"
igual "form vacío"            ""                       "$(extraer_base $'### Qué quieres\n\nalgo\n\n### Rama base (opcional)\n\n_No response_\n\n### Notas\n\nx')"
igual "form con valor"        "factory/issue-5-5"      "$(extraer_base $'### Rama base (opcional)\n\nfactory/issue-5-5\n\n### Notas\n\nx')"
igual "form con CRLF"         "factory/issue-5-5"      "$(extraer_base $'### Rama base (opcional)\r\n\r\nfactory/issue-5-5\r\n')"
igual "form con comillas"     "factory/issue-5-5"      "$(extraer_base $'### Rama base (opcional)\n\n`factory/issue-5-5`\n')"
igual "campo sin valor ni línea" ""                    "$(extraer_base $'### Rama base (opcional)\n### Notas\n\nx')"
igual "línea Base:"           "factory/issue-5-5"      "$(extraer_base $'Quiero convocatorias.\n\nBase: factory/issue-5-5\n')"
igual "línea Base: con CRLF"  "factory/issue-5-5"      "$(extraer_base $'Pedido\r\nBase:   factory/issue-5-5  \r\n')"
igual "form vacío + Base:"    "dev"                    "$(extraer_base $'### Rama base (opcional)\n\n_No response_\n\nBase: dev')"
igual "form gana a Base:"     "a"                      "$(extraer_base $'### Rama base (opcional)\n\na\n\nBase: b')"
igual "sin nada"              ""                       "$(extraer_base $'Solo un pedido\n\nque menciona la base de datos')"
igual "cuerpo vacío"          ""                       "$(extraer_base '')"
igual "Base: sin valor"       ""                       "$(extraer_base $'Base:\n')"
igual "valor malicioso (se extrae tal cual)" "main; rm -rf /" "$(extraer_base $'Base: main; rm -rf /')"

echo "nombre_valido"
for r in main factory/issue-5-5 feat_x.y release/1.2; do
  nombre_valido "$r" && ok "acepta $r" || mal "acepta $r" "válido" "rechazado"
done
for r in '' 'main; rm -rf /' '../x' 'a/../b' 'a..b' '-rf' '/abs' 'a b' 'a$(id)' 'a`id`' $'a\nb' 'a|b'; do
  q="$(printf '%q' "$r")"
  nombre_valido "$r" && mal "rechaza $q" "rechazado" "válido" || ok "rechaza $q"
done

echo "script completo (gh falso)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/gh" <<'GH'
#!/usr/bin/env bash
# gh falso: el cuerpo sale de $CUERPO, las ramas que existen de $RAMAS, y los
# comentarios se anotan en $TMPGH/comentarios.
case "$1 $2" in
  "issue view")    printf '%s' "$CUERPO" ;;
  "issue comment") cat >> "$TMPGH/comentarios" ;;
  "api repos/"*)   for r in $RAMAS; do [ "$2" = "repos/$GITHUB_REPOSITORY/branches/$r" ] && exit 0; done; exit 1 ;;
esac
GH
chmod +x "$tmp/gh"
export PATH="$tmp:$PATH" GITHUB_REPOSITORY=o/r TMPGH="$tmp" RAMAS="main factory/issue-5-5"

correr() { CUERPO="$1" "$DIR/rama-base.sh" 7 2>/dev/null; }
: > "$tmp/comentarios"
igual "sin base → main"            "main"              "$(correr 'pedido')"
igual "base existente"             "factory/issue-5-5" "$(correr $'Base: factory/issue-5-5')"
correr $'Base: factory/no-existe' >/dev/null; igual "rama inexistente falla" "1" "$?"
correr $'Base: ../x' >/dev/null;                igual "../x falla"           "1" "$?"
correr $'Base: main; rm -rf /' >/dev/null;      igual "inyección falla"      "1" "$?"
igual "comentó en el issue (3 fallos)" "3" "$(grep -c 'No arranqué' "$tmp/comentarios")"

echo
[ "$fallos" -eq 0 ] && echo "Todo en verde." || { echo "$fallos fallo(s)."; exit 1; }
