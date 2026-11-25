#!/usr/bin/env bash
#
# Crea (o actualiza) las etiquetas de la factory en el repo de GitHub.
# Se corre una vez, en local, con `gh` autenticado:
#
#   ./scripts/factory/crear-etiquetas.sh            # repo del directorio actual
#   ./scripts/factory/crear-etiquetas.sh dueño/repo
#
# Es idempotente: --force actualiza color y descripción si ya existen.

set -euo pipefail

REPO="${1:-}"

etiqueta() { # nombre color descripción
  # Sin arrays vacíos: el bash 3.2 de macOS los rechaza con `set -u`.
  if [ -n "$REPO" ]; then
    gh label create "$1" --color "$2" --description "$3" --force --repo "$REPO"
  else
    gh label create "$1" --color "$2" --description "$3" --force
  fi
}

# Las que pones tú: disparan la factory.
etiqueta "factory:feature"   "1D76DB" "Factory: planear esta feature (backend → shared → android)"
etiqueta "factory:paridad"   "5319E7" "Factory: auditar qué le falta a iOS frente a Android"
etiqueta "factory:aprobado"  "0E8A16" "Factory: plan aprobado, implementar y abrir PR"
etiqueta "factory:replanear" "FBCA04" "Factory: volver a planear leyendo mis respuestas"
etiqueta "factory:tests"     "C5DEF5" "Factory: cubrir lógica sin tests en una suite"
etiqueta "factory:barrido"   "C5DEF5" "Factory: barrer tokens y strings quemados en la UI"
etiqueta "factory:deps"      "C5DEF5" "Factory: subir y probar una dependencia"

# Las que pone la factory: solo informan, no disparan nada.
etiqueta "factory"            "EDEDED" "PR abierto por la factory"
etiqueta "factory:en-curso"   "BFD4F2" "La factory está trabajando en esto"
etiqueta "factory:plan-listo" "D4C5F9" "Hay un plan esperando tu aprobación"
etiqueta "factory:bloqueado"  "D93F0B" "La factory necesita una decisión tuya"
etiqueta "factory:pr-abierto" "C2E0C6" "La factory abrió un PR para este issue"
