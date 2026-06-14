#!/usr/bin/env bash
# Run Scala template golden tests (SQL + HTTP) with optional variant filter.
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
sql_suite='*SqlServiceCodegenTemplateTestSuite*'
http_suite='*HttpServiceCodegenTemplateTestSuite*'
munit_filter=""
run_sql=true
run_http=true

case "${target}" in
  python|python/db)
    echo "==> Python template golden tests (all db variants)"
    run_http=false
    ;;
  python/db/sqlite)
    echo "==> Python template golden tests (db sqlite)"
    run_http=false
    munit_filter='*src*db*sqlite*'
    ;;
  python/db/postgres)
    echo "==> Python template golden tests (db postgres)"
    run_http=false
    munit_filter='*src*db*postgres*'
    ;;
  python/api|python/http)
    echo "==> Python template golden tests (all http variants)"
    run_sql=false
    ;;
  python/api/fastapi)
    echo "==> Python template golden tests (api fastapi)"
    run_sql=false
    munit_filter='*src*api*fastapi*'
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

run_suite() {
  local suite="$1"
  if [[ -n "${munit_filter}" ]]; then
    sbtn "smithplatesPlugin/testOnly ${suite} -- -o ${munit_filter}"
  else
    sbtn "smithplatesPlugin/testOnly ${suite}"
  fi
}

if [[ "${run_sql}" == true ]]; then
  run_suite "${sql_suite}"
fi
if [[ "${run_http}" == true ]]; then
  run_suite "${http_suite}"
fi
