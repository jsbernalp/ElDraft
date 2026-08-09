#!/usr/bin/env bash
#
# Instala los git hooks del proyecto (que NO se versionan en .git/hooks).
# Corre esto una vez tras clonar el repo:
#   ./elDraft-app/scripts/install-hooks.sh

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOK_SRC="$REPO_ROOT/elDraft-app/scripts/pre-commit"
HOOK_DST="$REPO_ROOT/.git/hooks/pre-commit"

chmod +x "$REPO_ROOT/elDraft-app/scripts/check-design-tokens.sh" "$HOOK_SRC"
ln -sf "$HOOK_SRC" "$HOOK_DST"

echo "✓ Hook pre-commit instalado: $HOOK_DST -> $HOOK_SRC"
echo "  Verifica Design Tokens en los .kt de UI antes de cada commit."
