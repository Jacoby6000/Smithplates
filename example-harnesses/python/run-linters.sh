#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

ensure_example_env

mapfile -t python_files < <(collect_example_python_files)
if [[ ${#python_files[@]} -eq 0 ]]; then
  echo "error: no Python files found under ${EXAMPLE_ROOT}" >&2
  exit 1
fi

configure_example_env
run_example_linters "example/python" "${python_files[@]}"
