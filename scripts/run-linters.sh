#!/usr/bin/env bash
# Run SmithyStache linters and compilers from the repository root.
# Invoked directly:
#   scripts/run-linters.sh [all|scala|templates]
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

require_sbtn() {
  if ! command -v sbtn >/dev/null 2>&1; then
    echo "error: sbtn not on PATH. Install with: coursier install sbtn" >&2
    exit 1
  fi
}

run_scala_linters() {
  require_sbtn
  echo "==> Scala (scalafmt, scalafix, compile)"
  sbtn scalafmtCheckAll
  sbtn 'scalafixAll --check'
  sbtn compile
}

run_template_linters() {
  local found=0
  shopt -s nullglob
  for linter in language-test-harnesses/*/run-linters.sh; do
    found=1
    local lang
    lang="$(basename "$(dirname "${linter}")")"
    echo "==> ${lang} template linters"
    "${linter}"
  done
  if [[ ${found} -eq 0 ]]; then
    echo "error: no language-test-harnesses/*/run-linters.sh scripts found" >&2
    exit 1
  fi
}

run_all() {
  run_scala_linters
  run_template_linters
}

case "${1:-all}" in
  scala) run_scala_linters ;;
  templates) run_template_linters ;;
  all) run_all ;;
  *)
    echo "usage: $0 [all|scala|templates]" >&2
    exit 2
    ;;
esac
