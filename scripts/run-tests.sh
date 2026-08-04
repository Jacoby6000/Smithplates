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
    smithplatesSqlDdlRendererPostgres
    smithplatesSqlDdlRendererSqlite
    smithplatesSqlServiceQueryRenderer
    smithplatesSqlServiceQueryRendererPostgres
    smithplatesSqlServiceQueryRendererSqlite
    smithplatesSqlServiceRenderer
    smithplatesTestkit
    smithplatesSqlDdlRendererPostgresIt
    smithplatesSqlDdlRendererSqliteIt
  )
  local module
  for module in "${modules[@]}"; do
    sbtn "${module}/test"
  done
  sbtn 'smithplatesPlugin/testOnly com.jacoby6000.smithplates.plugin.SmithplatesSqlSettingsSpec com.jacoby6000.smithplates.plugin.SmithplatesHttpSettingsSpec com.jacoby6000.smithplates.plugin.ConsumerCodegenOutputsSpec com.jacoby6000.smithplates.plugin.ConsumerCodegenOutputValidatorSpec com.jacoby6000.smithplates.plugin.LanguageTargetTemplateValidatorSpec com.jacoby6000.smithplates.plugin.codegentest.TemplateBuildLogSpec'
}

run_template_golden_tests() {
  ./scripts/run-template-golden-tests.sh
}

run_template_language_tests() {
  local found=0
  shopt -s nullglob
  for runner in language-test-harnesses/*/run-tests.sh; do
    found=1
    local language
    language="$(basename "$(dirname "${runner}")")"
    echo "==> ${language} template tests"
    "${runner}"
  done
  if [[ ${found} -eq 0 ]]; then
    echo "error: no language-test-harnesses/*/run-tests.sh scripts found" >&2
    exit 1
  fi
}

run_template_tests() {
  run_template_golden_tests
  run_template_language_tests
}

run_all() {
  run_scala_tests
  run_template_language_tests
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
