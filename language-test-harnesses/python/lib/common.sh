#!/usr/bin/env bash
# Shared helpers for Python language-test-harness scripts.
# shellcheck shell=bash

_harness_lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HARNESS_DIR="$(cd "${_harness_lib_dir}/.." && pwd)"
REPO_ROOT="$(cd "${HARNESS_DIR}/../.." && pwd)"
TESTS_ROOT="${REPO_ROOT}/templates/python/tests"
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
  local derived_sql_test
  for derived_sql_test in "${test_dir}"/test_*_derived_sql.py; do
    if [[ -e "${derived_sql_test}" ]]; then
      return 0
    fi
  done
  return 1
}

run_python_linters() {
  local label="$1"
  shift
  local -a python_files=("$@")

  echo "==> ${label} ruff check"
  if ! uv run ruff check --config "${PYPROJECT}" "${python_files[@]}"; then
    return 1
  fi

  echo "==> ${label} ruff format --check"
  if ! uv run ruff format --check --config "${PYPROJECT}" "${python_files[@]}"; then
    return 1
  fi

  echo "==> ${label} mypy"
  if ! uv run mypy --strict --config-file "${PYPROJECT}" "${python_files[@]}"; then
    return 1
  fi
}

discover_python_service_types() {
  local -a service_types=()
  local case_dir service_type_dir service_type

  shopt -s nullglob
  for case_dir in "${TESTS_ROOT}"/*/; do
    for service_type_dir in "${case_dir}expected/src"/*/; do
      service_type="$(basename "${service_type_dir}")"
      local seen=0
      if [[ ${#service_types[@]} -gt 0 ]]; then
        local existing
        for existing in "${service_types[@]}"; do
          if [[ "${existing}" == "${service_type}" ]]; then
            seen=1
            break
          fi
        done
      fi
      if [[ ${seen} -eq 0 ]]; then
        service_types+=("${service_type}")
      fi
    done
  done

  if [[ ${#service_types[@]} -eq 0 ]]; then
    return 0
  fi

  printf '%s\n' "${service_types[@]}" | sort -u
}

resolve_python_service_types() {
  local service_type_filter="${SMITHYSTACHE_PYTHON_SERVICE_TYPE:-all}"
  local -a service_types=()

  if [[ "${service_type_filter}" == "all" ]]; then
    mapfile -t service_types < <(discover_python_service_types)
  else
    service_types=("${service_type_filter}")
  fi

  if [[ ${#service_types[@]} -eq 0 ]]; then
    echo "error: no Python service types discovered under ${TESTS_ROOT}" >&2
    return 1
  fi

  printf '%s\n' "${service_types[@]}"
}

resolve_python_impls() {
  local impl_filter="${SMITHYSTACHE_PYTHON_IMPL:-all}"
  case "${impl_filter}" in
    all) printf '%s\n' sqlite postgres ;;
    sqlite|postgres) printf '%s\n' "${impl_filter}" ;;
    *)
      echo "error: unknown Python impl filter: ${impl_filter}" >&2
      return 2
      ;;
  esac
}

foreach_python_variant() {
  local callback="$1"
  local failures=0
  local -a service_types=()
  local -a impls=()

  mapfile -t service_types < <(resolve_python_service_types) || return $?
  mapfile -t impls < <(resolve_python_impls) || return $?

  shopt -s nullglob
  for case_dir in "${TESTS_ROOT}"/*/; do
    local case_name
    case_name="$(basename "${case_dir}")"

    local service_type
    for service_type in "${service_types[@]}"; do
      local db_root="${case_dir}expected/src/${service_type}"

      local impl
      for impl in "${impls[@]}"; do
        local test_dir="${case_dir}expected/test/${service_type}/${impl}"
        if ! variant_has_derived_sql_tests "${db_root}" "${impl}" "${test_dir}"; then
          continue
        fi

        if ! "${callback}" "${case_name}" "${impl}" "${db_root}" "${test_dir}"; then
          failures=$((failures + 1))
        fi
      done
    done
  done

  if [[ ${failures} -gt 0 ]]; then
    echo "${failures} case variant(s) failed" >&2
    return 1
  fi
}
