#!/usr/bin/env bash
# Run linters for Smithplates example reference projects.
# Invoked directly:
#   scripts/run-example-linters.sh [all|python|typescript]
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"

run_example_harness_linters() {
  local project="$1"
  local linter="${ROOT}/example-harnesses/${project}/run-linters.sh"
  if [[ ! -x "${linter}" ]]; then
    echo "error: missing example linter script: ${linter}" >&2
    exit 1
  fi
  echo "==> ${project} example linters"
  "${linter}"
}

run_all() {
  local found=0
  shopt -s nullglob
  for linter in example-harnesses/*/run-linters.sh; do
    found=1
    local project
    project="$(basename "$(dirname "${linter}")")"
    run_example_harness_linters "${project}"
  done
  if [[ ${found} -eq 0 ]]; then
    echo "error: no example-harnesses/*/run-linters.sh scripts found" >&2
    exit 1
  fi
}

mode="${1:-all}"
case "${mode}" in
  all) run_all ;;
  python)
    run_example_harness_linters python
    ;;
  typescript)
    run_example_harness_linters typescript
    ;;
  *)
    echo "usage: $0 [all|python|typescript]" >&2
    exit 2
    ;;
esac
