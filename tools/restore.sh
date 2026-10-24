#!/usr/bin/env bash
# Put the TV back the way it was before tools/disable.sh, using the state recorded at that time:
# packages that were already disabled stay disabled, Home goes back to the component that owned it,
# and the animation scales go back to their recorded values. Pass the same list you ran disable with.
# The record has to come from this TV: one taken on another TV is refused, and with no record at all
# nothing is sent unless RESTORE_WITHOUT_RECORD=1 asks for every listed package to be enabled.
#
# Usage: TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt [stock.launcher.package]
#        RESTORE_WITHOUT_RECORD=1 TV_HOST=192.168.1.50 tools/restore.sh <list>   # no record: enable the whole list
set -euo pipefail
LIST="${1:?usage: restore.sh <debloat list> [stock launcher package]}"
# Resolve the list against the caller's directory before moving into the repo. Git Bash's pwd says
# /c/Users/..., which the Windows Python cannot open with MSYS_NO_PATHCONV=1 set; pwd -W gives
# C:/Users/..., the same path (and so the same record) as the PowerShell scripts.
LIST_DIR="$(cd "$(dirname "$LIST")" && { pwd -W 2>/dev/null || pwd; })"
LIST="$LIST_DIR/$(basename "$LIST")"
cd "$(dirname "$0")/.."
STOCK="${2:-com.google.android.tvlauncher}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
# recovery.py reads and validates the list itself, asks the TV for its id when a record exists,
# prints the batch for tv.py and says on stderr what it will leave alone. A record taken on another TV
# is refused; without a record it refuses too, unless RESTORE_WITHOUT_RECORD=1 asks it to fall back to
# enabling the whole list. A refused plan never reaches the TV because recovery.py prints nothing
# until the whole plan is built, so tv.py reads an empty batch; pipefail only makes this script exit
# non-zero afterwards.
"$PY" -I tools/recovery.py --commands "$LIST" "$STOCK" | "$PY" -I tools/tv.py --batch
