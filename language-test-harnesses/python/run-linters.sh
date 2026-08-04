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

run_http_case_linters() {
  local case_name="$1"
  local case_dir="$2"
  local src_root="${case_dir}expected/src"
  local label="${case_name} / http"

  mapfile -t python_files < <(find "${src_root}" -name '*.py' -type f; find "${case_dir}" -maxdepth 1 -name 'test_*.py' -type f)
  mapfile -t mypy_files < <(while IFS= read -r path; do printf '%s\n' "${case_dir}${path}"; done < "${case_dir}mypy-files.txt")
  export PYTHONPATH="${src_root}"
  export PYTHONDONTWRITEBYTECODE=1
  export MYPYPATH="${src_root}"

  echo "==> ${label} ruff check"
  uv run ruff check --config "${PYPROJECT}" "${python_files[@]}"
  echo "==> ${label} ruff format --check"
  uv run ruff format --check --config "${PYPROJECT}" "${python_files[@]}"
  echo "==> ${label} mypy"
  uv run mypy --strict --explicit-package-bases --config-file "${PYPROJECT}" "${mypy_files[@]}"
}

foreach_python_http_case run_http_case_linters
