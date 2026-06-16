#!/usr/bin/env bash
# Run tests for Smithplates example reference projects.
# Invoked directly:
#   scripts/run-example-tests.sh [all|python]
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"

run_example_harness_tests() {
  local project="$1"
  local build_target="$2"
  "${ROOT}/scripts/run-example-build.sh" "${build_target}"
  local runner="${ROOT}/example-harnesses/${project}/run-tests.sh"
  if [[ ! -x "${runner}" ]]; then
    echo "error: missing example test script: ${runner}" >&2
    exit 1
  fi
  echo "==> ${project} example tests"
  "${runner}"
}

run_all() {
  run_example_harness_tests python all
}

mode="${1:-all}"
case "${mode}" in
  all) run_all ;;
  python) run_example_harness_tests python python ;;
  *)
    echo "usage: $0 [all|python]" >&2
    exit 2
    ;;
esac
