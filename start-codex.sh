#!/data/data/com.termux/files/usr/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DATA_DIR=${BYD_CODEX_DATA_DIR:-"$HOME/storage/shared/Download/BydDilink"}
REPORTS_DIR=${BYD_INSPECTOR_REPORTS_DIR:-"$DATA_DIR/Inspector"}

if ! command -v codex >/dev/null 2>&1; then
    printf '%s\n' 'Codex CLI não foi encontrado no PATH.' >&2
    exit 127
fi

mkdir -p "$DATA_DIR" "$REPORTS_DIR"
cd "$PROJECT_DIR"
exec codex -C "$PROJECT_DIR" -m gpt-6-luna -s workspace-write -a never -c "notify=[\"python\", \"$PROJECT_DIR/.codex/speak-codex-reply.py\"]" --add-dir "$DATA_DIR" --add-dir "$REPORTS_DIR" "$@"
