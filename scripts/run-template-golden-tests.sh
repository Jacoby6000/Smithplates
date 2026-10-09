#!/usr/bin/env bash
# Run Scala template golden tests (single CodegenTemplateTestSuite).
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

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
  all|rust)
    echo "==> Shared template golden tests (all language variants)"
    ;;
  *)
    if smithystache_validate_target_is_python "${target}"; then
      echo "==> Python template golden tests"
    else
      echo "error: template golden tests require a python or rust validate target (got ${target})" >&2
      exit 2
    fi
    ;;
esac

# Some experimental thin-client startup failures print a JVM exception but return
# zero. Require the MUnit completion summary as well as a successful exit so a
# failed connection cannot silently skip generation/comparison and run stale output.
mkdir -p "${ROOT}/target"
log="$(mktemp "${ROOT}/target/template-golden-tests.XXXXXX")"
trap 'rm -f "${log}"' EXIT
sbtn "smithplatesPlugin/testOnly ${suite}" 2>&1 | tee "${log}"
if ! grep -Eq 'Passed: Total [1-9][0-9]*, Failed 0, Errors 0,' "${log}"; then
  echo "error: template golden tests did not report successful completion" >&2
  exit 1
fi
