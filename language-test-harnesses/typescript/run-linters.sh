#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
source "${ROOT}/example-harnesses/typescript/lib/common.sh"

ensure_example_env

shopt -s nullglob
for config in "${ROOT}"/templates/typescript/tests/*/tsconfig.json; do
  case_dir="$(dirname "${config}")"
  echo "==> $(basename "${case_dir}") / typescript tsc --noEmit"
  "${EXAMPLE_ROOT}/node_modules/.bin/tsc" --noEmit --project "${config}"
done
