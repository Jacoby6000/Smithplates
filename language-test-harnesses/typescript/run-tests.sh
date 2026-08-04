#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
source "${ROOT}/example-harnesses/typescript/lib/common.sh"

ensure_example_env

shopt -s nullglob
for config in "${ROOT}"/templates/typescript/tests/*/tsconfig.json; do
  case_dir="$(dirname "${config}")"
  tests=("${case_dir}"/*.test.ts)
  if [[ ${#tests[@]} -eq 0 ]]; then
    continue
  fi
  echo "==> $(basename "${case_dir}") / typescript tests"
  "${EXAMPLE_ROOT}/node_modules/.bin/tsx" --tsconfig "${config}" --test "${tests[@]}"
done
