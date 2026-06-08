#!/usr/bin/env bash
# Run SmithyStache Scala and Python template test suites from the repository root.
# Invoked directly:
#   scripts/run-tests.sh [all|scala|templates]
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

require_sbtn() {
  if ! command -v sbtn >/dev/null 2>&1; then
    echo "error: sbtn not on PATH. Install with: coursier install sbtn" >&2
    exit 1
  fi
}

run_scala_tests() {
  require_sbtn
  echo "==> Scala tests (all aggregated modules; Docker required for *RendererIt)"
  sbtn test
}

run_template_tests() {
  echo "==> Python template harness (uv required; Docker required for postgres variants)"
  ./language-test-harnesses/python/run-tests.sh
}

run_all() {
  run_scala_tests
  run_template_tests
}

case "${1:-all}" in
  scala) run_scala_tests ;;
  templates) run_template_tests ;;
  all) run_all ;;
  *)
    echo "usage: $0 [all|scala|templates]" >&2
    exit 2
    ;;
esac
