#!/usr/bin/env bash
# Run Smithplates test suites from the repository root (no linters).
# Invoked directly:
#   scripts/run-tests.sh [all|scala|templates|plugin]
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

run_plugin_tests() {
  require_sbtn
  echo "==> Scala plugin tests (excluding template golden suite; Docker required for *RendererIt)"
  local -a modules=(
    smithplatesSqlIr
    smithplatesSqlServiceIr
    smithplatesSqlPostgresRenderer
    smithplatesSqlSqliteRenderer
    smithplatesSqlServiceQueryRenderer
    smithplatesSqlServiceQueryRendererPostgres
    smithplatesSqlServiceQueryRendererSqlite
    smithplatesSqlServiceRenderer
    smithplatesTestkit
    smithplatesSqlPostgresRendererIt
    smithplatesSqlSqliteRendererIt
  )
  local module
  for module in "${modules[@]}"; do
    sbtn "${module}/test"
  done
  sbtn 'smithplatesPlugin/testOnly com.jacoby6000.smithplates.SmithplatesSqlSettingsSpec'
}

run_template_golden_tests() {
  ./scripts/run-template-golden-tests.sh
}

run_template_pytest() {
  echo "==> Python template tests (uv required; Docker required for postgres variants)"
  ./language-test-harnesses/python/run-tests.sh
}

run_template_tests() {
  run_template_golden_tests
  run_template_pytest
}

run_all() {
  run_scala_tests
  run_template_pytest
}

mode="${1:-all}"
case "${mode}" in
  scala) run_scala_tests ;;
  plugin) run_plugin_tests ;;
  templates) run_template_tests ;;
  all) run_all ;;
  *)
    echo "usage: $0 [all|scala|templates|plugin]" >&2
    exit 2
    ;;
esac
