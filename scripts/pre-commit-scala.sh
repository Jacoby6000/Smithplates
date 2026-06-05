#!/usr/bin/env bash
# Git pre-commit helper: scalafmt, scalafix, and sbt compile.
# Invoked by pre-commit (.pre-commit-config.yaml) or directly:
#   scripts/pre-commit-scala.sh [fmt|fix|compile|all]
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

require_sbtn() {
  if ! command -v sbtn >/dev/null 2>&1; then
    echo "error: sbtn not on PATH. Install with: coursier install sbtn" >&2
    exit 1
  fi
}

fail_if_sources_modified() {
  local changed
  changed="$(git diff --name-only -- '*.scala' '*.sbt' || true)"
  if [ -n "$changed" ]; then
    echo "error: scalafmt/scalafix updated Scala sources. Stage the changes and commit again:" >&2
    printf '  %s\n' $changed >&2
    exit 1
  fi
}

run_fmt() {
  require_sbtn
  sbtn scalafmtAll
  fail_if_sources_modified
}

run_fix() {
  require_sbtn
  sbtn scalafixAll
  fail_if_sources_modified
}

run_compile() {
  require_sbtn
  sbtn compile
}

run_all() {
  run_fmt
  run_fix
  fail_if_sources_modified
  run_compile
}

case "${1:-all}" in
  fmt) run_fmt ;;
  fix) run_fix ;;
  compile) run_compile ;;
  all) run_all ;;
  *)
    echo "usage: $0 [fmt|fix|compile|all]" >&2
    exit 2
    ;;
esac
