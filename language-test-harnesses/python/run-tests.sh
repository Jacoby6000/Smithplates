#!/usr/bin/env bash
# Run @pytest.mark.integration suites from templates/python/tests golden expected/ trees.
set -euo pipefail

harness_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${harness_dir}/lib/common.sh"

export PYTHONUNBUFFERED=1

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
  echo "==> pytest ${label}"
  uv run pytest "${common_args[@]}" -m "integration and ${impl}" "${test_dir}"
}

foreach_python_variant run_variant_tests

run_http_case_tests() {
  local case_name="$1"
  local case_dir="$2"
  local src_root="${case_dir}expected/src"

  export PYTHONPATH="${src_root}"
  export PYTHONDONTWRITEBYTECODE=1
  echo "==> pytest ${case_name} / http"
  uv run pytest "${common_args[@]}" --confcutdir="${case_dir}" "${case_dir}"/test_*.py
}

foreach_python_http_case run_http_case_tests
