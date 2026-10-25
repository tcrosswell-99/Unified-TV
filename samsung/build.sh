#!/usr/bin/env bash
# Unified TV for Samsung Frame: build the UNSIGNED development package.
#
#   bash samsung/build.sh
#
# Produces samsung/out/UnifiedTV-Frame-unsigned.wgt (a zip of the web app), samsung/out/SHA256SUMS.txt,
# samsung/out/manifest.txt and the staged folder samsung/out/UnifiedTV-Frame/ that the owner signs.
# Needs only python3 (standard library). Runs the Node tests first when Node 18 or later is installed.
# It never signs anything and never touches a TV. Signing and installing: samsung/README.md.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v node >/dev/null 2>&1 && node -e 'process.exit(parseInt(process.versions.node, 10) >= 18 ? 0 : 1)'; then
  node --test "$here"/test/*.test.js
else
  echo "build: Node 18+ not found, skipping samsung/test (CI runs them)"
fi

python3 "$here/tools/package.py"
