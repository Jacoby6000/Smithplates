#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

ensure_uv_env

run_variant_linters() {
  local case_name="$1"
  local impl="$2"
  local db_root="$3"
  local test_dir="$4"
  local label="${case_name} / ${impl}"

  mapfile -t python_files < <(collect_python_files "${db_root}" "${impl}" "${test_dir}")
  if [[ ${#python_files[@]} -eq 0 ]]; then
    echo "no Python files to lint for ${label}" >&2
    return 1
  fi

  configure_case_env "${db_root}" "${impl}" "${test_dir}"
  run_python_linters "${label}" "${python_files[@]}"
}

foreach_python_variant run_variant_linters
