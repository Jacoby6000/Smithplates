#!/usr/bin/env bash
# Run Scala template golden tests (single CodegenTemplateTestSuite).
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

"${ROOT}/scripts/clean-template-test-artifacts.sh"

if ! command -v sbtn >/dev/null 2>&1; then
  echo "error: sbtn not on PATH. Install with: coursier install sbtn" >&2
  exit 1
fi

# shellcheck source=scripts/lib/validate-target.sh
source "${ROOT}/scripts/lib/validate-target.sh"

target="${SMITHYSTACHE_VALIDATE_TARGET:-all}"
suite='*CodegenTemplateTestSuite*'

# All comparison tests share a single per-case `build - <case>` test (one Smithy build per
# fixture). A munit `-- -o <glob>` name filter would exclude those prerequisite build tests and
# break the dependency, so every target runs the full suite once; the per-case build dominates
# runtime regardless, and variant comparisons are effectively free.
case "${target}" in
  python|python/db)
    echo "==> Python template golden tests (db variants share the full suite run)"
    ;;
  python/db/sqlite)
    echo "==> Python template golden tests (db sqlite; full suite run)"
    ;;
  python/db/postgres)
    echo "==> Python template golden tests (db postgres; full suite run)"
    ;;
  python/api|python/http)
    echo "==> Python template golden tests (http variants share the full suite run)"
    ;;
  python/api/fastapi)
    echo "==> Python template golden tests (api fastapi; full suite run)"
    ;;
  all)
    echo "==> Python template golden tests (all variants)"
    ;;
  *)
    if smithystache_validate_target_is_python "${target}"; then
      echo "==> Python template golden tests"
    else
      echo "error: template golden tests require a python validate target (got ${target})" >&2
      exit 2
    fi
    ;;
esac

sbtn "smithplatesPlugin/testOnly ${suite}"
