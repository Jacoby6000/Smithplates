#!/usr/bin/env bash
# Shared helpers for Python language-test-harness scripts.
# shellcheck shell=bash

_harness_lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HARNESS_DIR="$(cd "${_harness_lib_dir}/.." && pwd)"
REPO_ROOT="$(cd "${HARNESS_DIR}/../.." && pwd)"
TESTS_ROOT="${REPO_ROOT}/templates/python/tests"
PYPROJECT="${HARNESS_DIR}/pyproject.toml"
GENERATED_SOURCE_ROOT="src/generated"

ensure_uv_env() {
  cd "${HARNESS_DIR}"
  uv sync
}

collect_python_files() {
  local namespace_root="$1"
  local impl="$2"
  local test_dir="$3"

  if [[ -d "${namespace_root}/models" ]]; then
    find "${namespace_root}/models" -name '*.py' -type f
  fi
  find "${namespace_root}" -maxdepth 1 -name '*.py' -type f
  if [[ -d "${namespace_root}/${impl}" ]]; then
    find "${namespace_root}/${impl}" -name '*.py' -type f
  fi
  find "${test_dir}" -maxdepth 1 -name 'test_*.py' -type f
}

configure_case_env() {
  local namespace_root="$1"
  local impl="$2"
  local test_dir="$3"
  local src_root
  src_root="$(dirname "$(dirname "${namespace_root}")")"

  export PYTHONPATH="${src_root}"
  export PYTHONDONTWRITEBYTECODE=1
  local mypy_path="${src_root}"
  if [[ -d "${test_dir}/stubs" ]]; then
    mypy_path="${test_dir}/stubs:${mypy_path}"
  fi
  export MYPYPATH="${mypy_path}"
}

variant_has_derived_sql_tests() {
  local namespace_root="$1"
  local impl="$2"
  local test_dir="$3"
  local unsupported="${namespace_root}/${impl}/unsupported.md"

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
  if ! uv run mypy --strict --explicit-package-bases --config-file "${PYPROJECT}" "${python_files[@]}"; then
    return 1
  fi
}

discover_python_namespaces_for_case() {
  local case_dir="$1"
  local generated_root="${case_dir}expected/${GENERATED_SOURCE_ROOT}"

  if [[ ! -d "${generated_root}" ]]; then
    return 0
  fi

  shopt -s nullglob
  local ns_dir
  for ns_dir in "${generated_root}"/*/; do
    basename "${ns_dir}"
  done
}

discover_python_namespaces() {
  local -a namespaces=()
  local case_dir ns

  shopt -s nullglob
  for case_dir in "${TESTS_ROOT}"/*/; do
    for ns in $(discover_python_namespaces_for_case "${case_dir}"); do
      local seen=0
      if [[ ${#namespaces[@]} -gt 0 ]]; then
        local existing
        for existing in "${namespaces[@]}"; do
          if [[ "${existing}" == "${ns}" ]]; then
            seen=1
            break
          fi
        done
      fi
      if [[ ${seen} -eq 0 ]]; then
        namespaces+=("${ns}")
      fi
    done
  done

  if [[ ${#namespaces[@]} -eq 0 ]]; then
    return 0
  fi

  printf '%s\n' "${namespaces[@]}" | sort -u
}

resolve_python_namespaces() {
  local namespace_filter="${SMITHYSTACHE_PYTHON_SERVICE_TYPE:-all}"
  local -a namespaces=()

  if [[ "${namespace_filter}" == "all" || "${namespace_filter}" == "db" ]]; then
    mapfile -t namespaces < <(discover_python_namespaces)
  else
    namespaces=("${namespace_filter}")
  fi

  if [[ ${#namespaces[@]} -eq 0 ]]; then
    echo "error: no Python namespaces discovered under ${TESTS_ROOT}/*/expected/${GENERATED_SOURCE_ROOT}" >&2
    return 1
  fi

  printf '%s\n' "${namespaces[@]}"
}

should_run_sql_golden_case() {
  local case_filter="${SMITHYSTACHE_PYTHON_SERVICE_TYPE:-all}"
  local case_name="$1"

  if [[ "${case_filter}" == "db" ]]; then
    [[ "${case_name}" == sql-* ]]
  else
    return 0
  fi
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
  local -a namespaces=()
  local -a impls=()

  mapfile -t namespaces < <(resolve_python_namespaces) || return $?
  mapfile -t impls < <(resolve_python_impls) || return $?

  shopt -s nullglob
  for case_dir in "${TESTS_ROOT}"/*/; do
    local case_name
    case_name="$(basename "${case_dir}")"

    if ! should_run_sql_golden_case "${case_name}"; then
      continue
    fi

    local namespace
    for namespace in "${namespaces[@]}"; do
      local namespace_root="${case_dir}expected/${GENERATED_SOURCE_ROOT}/${namespace}"

      local impl
      for impl in "${impls[@]}"; do
        local test_dir="${case_dir}expected/test/${namespace}/${impl}"
        if ! variant_has_derived_sql_tests "${namespace_root}" "${impl}" "${test_dir}"; then
          continue
        fi

        if ! "${callback}" "${case_name}" "${impl}" "${namespace_root}" "${test_dir}"; then
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

foreach_python_http_case() {
  local callback="$1"
  local failures=0

  if [[ "${SMITHYSTACHE_PYTHON_SERVICE_TYPE:-all}" == "db" ]]; then
    return 0
  fi

  shopt -s nullglob
  local case_dir
  for case_dir in "${TESTS_ROOT}"/http-*/; do
    if [[ ! -f "${case_dir}mypy-files.txt" ]]; then
      continue
    fi
    if ! "${callback}" "$(basename "${case_dir}")" "${case_dir}"; then
      failures=$((failures + 1))
    fi
  done

  if [[ ${failures} -gt 0 ]]; then
    echo "${failures} HTTP case(s) failed" >&2
    return 1
  fi
}
