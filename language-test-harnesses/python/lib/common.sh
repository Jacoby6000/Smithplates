#!/usr/bin/env bash
# Shared helpers for Python language-test-harness scripts.
# shellcheck shell=bash

_harness_lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HARNESS_DIR="$(cd "${_harness_lib_dir}/.." && pwd)"
REPO_ROOT="$(cd "${HARNESS_DIR}/../.." && pwd)"
EXPECTED_ROOT="${REPO_ROOT}/templates/python/expected-outputs"
PYPROJECT="${HARNESS_DIR}/pyproject.toml"

ensure_uv_env() {
  cd "${HARNESS_DIR}"
  uv sync
}

collect_python_files() {
  local db_root="$1"
  local impl="$2"
  local test_dir="$3"

  if [[ -d "${db_root}/model" ]]; then
    find "${db_root}/model" -name '*.py' -type f
  fi
  find "${db_root}" -maxdepth 1 -name '*.py' -type f
  if [[ -d "${db_root}/${impl}" ]]; then
    find "${db_root}/${impl}" -name '*.py' -type f
  fi
  find "${test_dir}" -maxdepth 1 -name 'test_*.py' -type f
}

configure_case_env() {
  local db_root="$1"
  local impl="$2"
  local test_dir="$3"

  export PYTHONPATH="${db_root}/model:${db_root}:${db_root}/${impl}"
  export PYTHONDONTWRITEBYTECODE=1
  local mypy_path="${PYTHONPATH}"
  if [[ -d "${test_dir}/stubs" ]]; then
    mypy_path="${test_dir}/stubs:${mypy_path}"
  fi
  export MYPYPATH="${mypy_path}"
}

variant_has_derived_sql_tests() {
  local db_root="$1"
  local impl="$2"
  local test_dir="$3"
  local unsupported="${db_root}/${impl}/unsupported.md"

  [[ -d "${test_dir}" ]] || return 1
  [[ -f "${unsupported}" ]] && return 1
  compgen -G "${test_dir}/test_*_derived_sql.py" >/dev/null
}

run_python_linters() {
  local label="$1"
  shift
  local -a python_files=("$@")

  echo "==> ${label} ruff check"
  uv run ruff check --config "${PYPROJECT}" "${python_files[@]}"

  echo "==> ${label} ruff format --check"
  uv run ruff format --check --config "${PYPROJECT}" "${python_files[@]}"

  echo "==> ${label} mypy"
  uv run mypy --strict --config-file "${PYPROJECT}" "${python_files[@]}"
}

foreach_python_variant() {
  local callback="$1"
  local failures=0

  shopt -s nullglob
  for case_dir in "${EXPECTED_ROOT}"/*/; do
    local case_name
    case_name="$(basename "${case_dir}")"
    local db_root="${case_dir}src/db"

    for impl in sqlite postgres; do
      local test_dir="${case_dir}test/db/${impl}"
      if ! variant_has_derived_sql_tests "${db_root}" "${impl}" "${test_dir}"; then
        continue
      fi

      if ! "${callback}" "${case_name}" "${impl}" "${db_root}" "${test_dir}"; then
        failures=$((failures + 1))
      fi
    done
  done

  if [[ ${failures} -gt 0 ]]; then
    echo "${failures} case variant(s) failed" >&2
    return 1
  fi
}
