#!/usr/bin/env bash
# Run tests for Smithplates example reference projects.
# Invoked directly:
#   scripts/run-example-tests.sh [all|python]
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"

run_example_harness_tests() {
  local project="$1"
  local runner="${ROOT}/example-harnesses/${project}/run-tests.sh"
  if [[ ! -x "${runner}" ]]; then
    echo "error: missing example test script: ${runner}" >&2
    exit 1
  fi
  echo "==> ${project} example tests"
  "${runner}"
}

run_all() {
  local found=0
  shopt -s nullglob
  for runner in example-harnesses/*/run-tests.sh; do
    found=1
    local project
    project="$(basename "$(dirname "${runner}")")"
    run_example_harness_tests "${project}"
  done
  if [[ ${found} -eq 0 ]]; then
    echo "error: no example-harnesses/*/run-tests.sh scripts found" >&2
    exit 1
  fi
}

mode="${1:-all}"
case "${mode}" in
  all) run_all ;;
  python) run_example_harness_tests python ;;
  *)
    echo "usage: $0 [all|python]" >&2
    exit 2
    ;;
esac
