#!/usr/bin/env bash
set -euo pipefail

harness_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${harness_dir}/lib/common.sh"

basetemp="${REPO_ROOT}/target/language-test-harnesses/python/pytest-tmp"
mkdir -p "${basetemp}"
ensure_uv_env

common_args=(
  -c "${PYPROJECT}"
  --basetemp="${basetemp}"
  -p no:cacheprovider
)

if [[ $# -gt 0 ]]; then
  exec uv run pytest "${common_args[@]}" "$@"
fi

run_variant_tests() {
  local case_name="$1"
  local impl="$2"
  local db_root="$3"
  local test_dir="$4"
  local label="${case_name} / ${impl}"

  configure_case_env "${db_root}" "${impl}" "${test_dir}"

  echo "==> ${label} pytest"
  uv run pytest "${common_args[@]}" -m "integration and ${impl}" "${test_dir}"
}

foreach_python_variant run_variant_tests
