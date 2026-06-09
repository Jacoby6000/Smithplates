#!/usr/bin/env bash
# Run Scala template golden tests (SqlServiceCodegenTemplateTestSuite) with optional dialect filter.
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
suite='*SqlServiceCodegenTemplateTestSuite*'
munit_filter=""

case "${target}" in
  python|python/db)
    echo "==> Python template golden tests (all db variants)"
    ;;
  python/db/sqlite)
    echo "==> Python template golden tests (db sqlite)"
    munit_filter='*src*db*sqlite*'
    ;;
  python/db/postgres)
    echo "==> Python template golden tests (db postgres)"
    munit_filter='*src*db*postgres*'
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

if [[ -n "${munit_filter}" ]]; then
  sbtn "smithplatesPlugin/testOnly ${suite} -- -o ${munit_filter}"
else
  sbtn "smithplatesPlugin/testOnly ${suite}"
fi
