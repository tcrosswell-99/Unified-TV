#!/usr/bin/env bash
# Unified TV DLNA discovery proof of concept: test, then build the UNSIGNED, test-only package.
#
#   bash samsung/poc-dlna/build.sh
#
# Produces samsung/poc-dlna/out/UnifiedTV-DLNA-POC-unsigned.wgt, SHA256SUMS.txt, manifest.txt and the staged folder
# samsung/poc-dlna/out/UnifiedTV-DLNA-POC/ that the owner signs. Prototype only: a separate package (UTVDLNAPOC),
# never part of the Unified TV app. Needs python3; runs the Node tests first when Node 18 or later is installed.
# It never signs anything and never touches a TV.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v node >/dev/null 2>&1 && node -e 'process.exit(parseInt(process.versions.node, 10) >= 18 ? 0 : 1)'; then
  node --test "$here"/test/*.test.js
else
  echo "poc-build: Node 18+ not found, skipping samsung/poc-dlna/test (CI runs them)"
fi

python3 "$here/tools/package.py"
