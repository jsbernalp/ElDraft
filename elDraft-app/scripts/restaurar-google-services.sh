#!/usr/bin/env bash
#
# Restaura androidApp/google-services.json desde el secret GOOGLE_SERVICES_JSON
# (el archivo en base64), el mismo que usa android-release.yml.
#
# El archivo está gitignored —cada quien lo baja de la consola de Firebase— y el
# plugin com.google.gms.google-services no compila sin él, ni siquiera para
# tests unitarios: AndroidModule usa R.string.default_web_client_id, que genera
# ese plugin. Por eso cualquier job que corra :androidApp lo necesita.
#
# Uso, desde elDraft-app/ y con GOOGLE_SERVICES_JSON en el entorno:
#   ./scripts/restaurar-google-services.sh             # falla si falta el secret
#   ./scripts/restaurar-google-services.sh --opcional  # avisa y sigue (p. ej. @claude)
#
# Nunca pisa un archivo que ya exista (en tu máquina no hace nada). Y como está
# gitignored, la factory nunca lo commitea.
#
# Los PRs que vienen de forks o de Dependabot no reciben los secrets del repo:
# en esos, el modo estricto falla con un mensaje que lo dice.

set -euo pipefail

OPCIONAL=false
[ "${1:-}" = --opcional ] && OPCIONAL=true

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DESTINO="$DIR/../androidApp/google-services.json"

if [ -e "$DESTINO" ]; then
  echo "Ya hay un androidApp/google-services.json; no lo toco."
  exit 0
fi

if [ -z "${GOOGLE_SERVICES_JSON:-}" ]; then
  if [ "$OPCIONAL" = true ]; then
    echo "::warning title=Sin google-services.json::Falta el secret GOOGLE_SERVICES_JSON: :androidApp no va a compilar en esta corrida."
    exit 0
  fi
  echo "::error title=Falta GOOGLE_SERVICES_JSON::Sin el secret (el androidApp/google-services.json en base64) :androidApp no compila. Los PRs desde forks o de Dependabot no reciben secrets."
  exit 1
fi

# Se decodifica aparte y se valida antes de dejarlo en su sitio: un base64
# cortado al pegarlo daría un JSON roto, y el fallo aparecería mucho después en
# el plugin, con un mensaje que no menciona el secret.
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
if ! printf '%s' "$GOOGLE_SERVICES_JSON" | base64 --decode > "$TMP" 2>/dev/null \
   || ! grep -q '"project_id"' "$TMP"; then
  echo "::error title=GOOGLE_SERVICES_JSON inválido::El secret no decodifica a un google-services.json válido. Revisa el base64."
  exit 1
fi
mv "$TMP" "$DESTINO"
trap - EXIT
echo "google-services.json restaurado desde el secret."
