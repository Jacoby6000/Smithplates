#!/usr/bin/env bash
# Shared helpers for Python example validation scripts.
# shellcheck shell=bash

_harness_lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HARNESS_DIR="$(cd "${_harness_lib_dir}/.." && pwd)"
REPO_ROOT="$(cd "${HARNESS_DIR}/../.." && pwd)"
EXAMPLE_ROOT="${REPO_ROOT}/example/python"

ensure_example_env() {
  cd "${EXAMPLE_ROOT}"
  uv sync
}

collect_example_python_files() {
  local path
  for path in \
    "${EXAMPLE_ROOT}/src/server" \
    "${EXAMPLE_ROOT}/src/generated/http/server" \
    "${EXAMPLE_ROOT}/src/generated/http/client" \
    "${EXAMPLE_ROOT}/src/generated/http/models" \
    "${EXAMPLE_ROOT}/src/generated/db" \
    "${EXAMPLE_ROOT}/tests"; do
    if [[ -d "${path}" ]]; then
      find "${path}" -name '*.py' -type f ! -path '*/stubs/*'
    fi
  done
}

collect_example_handwritten_python_files() {
  local path
  for path in "${EXAMPLE_ROOT}/src/server" "${EXAMPLE_ROOT}/tests"; do
    if [[ -d "${path}" ]]; then
      find "${path}" -name '*.py' -type f ! -path '*/stubs/*'
    fi
  done
}

configure_example_env() {
  export PYTHONPATH="${EXAMPLE_ROOT}/src:${EXAMPLE_ROOT}/src/generated:${EXAMPLE_ROOT}/src/generated/db/model:${EXAMPLE_ROOT}/src/generated/db:${EXAMPLE_ROOT}/src/generated/db/sqlite:${EXAMPLE_ROOT}/src/generated/db/postgres:${EXAMPLE_ROOT}/src"
  export PYTHONDONTWRITEBYTECODE=1
  export MYPYPATH="${EXAMPLE_ROOT}/tests/db/postgres/stubs:${PYTHONPATH}"
}

run_example_linters() {
  local label="$1"
  shift
  local -a python_files=("$@")
  local -a relative_files=()
  local -a relative_format_files=()
  local -a mypy_targets=(
    src/server
    tests/test_api.py
    tests/db/sqlite
  )
  local -a postgres_tests=()
  local path target

  mapfile -t format_files < <(collect_example_handwritten_python_files)
  for path in "${format_files[@]}"; do
    relative_format_files+=("${path#"${EXAMPLE_ROOT}/"}")
  done
  for path in "${python_files[@]}"; do
    relative_files+=("${path#"${EXAMPLE_ROOT}/"}")
  done

  cd "${EXAMPLE_ROOT}"

  echo "==> ${label} ruff check"
  if ! uv run --group dev ruff check --config pyproject.toml "${relative_files[@]}"; then
    return 1
  fi

  echo "==> ${label} ruff format --check (hand-written)"
  if ! uv run --group dev ruff format --check --config pyproject.toml "${relative_format_files[@]}"; then
    return 1
  fi

  echo "==> ${label} mypy"
  for target in "${mypy_targets[@]}"; do
    if [[ ! -e "${target}" ]]; then
      continue
    fi
    echo "==> ${label} mypy ${target}"
    if ! uv run --group dev mypy "${target}"; then
      return 1
    fi
  done

  shopt -s nullglob
  postgres_tests=(tests/db/postgres/test_*.py)
  if [[ ${#postgres_tests[@]} -gt 0 ]]; then
    echo "==> ${label} mypy tests/db/postgres (derived SQL)"
    if ! uv run --group dev mypy "${postgres_tests[@]}"; then
      return 1
    fi
  fi
}
