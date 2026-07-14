#!/usr/bin/env bash
# Shared helpers for TypeScript example validation scripts.
# shellcheck shell=bash

_harness_lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HARNESS_DIR="$(cd "${_harness_lib_dir}/.." && pwd)"
REPO_ROOT="$(cd "${HARNESS_DIR}/../.." && pwd)"
EXAMPLE_ROOT="${REPO_ROOT}/example/typescript"

ensure_example_env() {
  cd "${EXAMPLE_ROOT}"
  if ! command -v npm >/dev/null 2>&1; then
    echo "error: npm not on PATH" >&2
    exit 1
  fi
  if [[ ! -d "${EXAMPLE_ROOT}/node_modules" ]]; then
    npm install
  fi
}

collect_example_typescript_files() {
  local path
  for path in \
    "${EXAMPLE_ROOT}/src/generated" \
    "${EXAMPLE_ROOT}/tests"; do
    if [[ -d "${path}" ]]; then
      find "${path}" -name '*.ts' -type f
    fi
  done
}
