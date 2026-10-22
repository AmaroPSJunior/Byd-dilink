#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

show_file() {
    file=$1
    if [ -f "$file" ]; then
        printf '\n%s\n' "===== ${file#"$PROJECT_DIR"/} ====="
        cat "$file"
    fi
}

show_file "$PROJECT_DIR/AGENTS.md"
show_file "$PROJECT_DIR/docs/STATUS.md"
show_file "$PROJECT_DIR/docs/STATUS.local.md"

printf '\n%s\n' 'Contexto carregado. Use os caminhos acima para continuar a tarefa.'
