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

run_batched_impl_tests() {
  local impl="$1"
  local -a test_dirs=()
  local -a pythonpath_parts=()
  local -a mypypath_parts=()

  while IFS=$'\t' read -r _case_name variant_impl db_root test_dir; do
    [[ "${variant_impl}" == "${impl}" ]] || continue
    test_dirs+=("${test_dir}")
    pythonpath_parts+=("${db_root}/model" "${db_root}" "${db_root}/${impl}")
    if [[ -d "${test_dir}/stubs" ]]; then
      mypypath_parts+=("${test_dir}/stubs")
    fi
  done < <(enumerate_python_variants)

  if [[ ${#test_dirs[@]} -eq 0 ]]; then
    return 0
  fi

  export PYTHONPATH
  PYTHONPATH="$(IFS=:; echo "${pythonpath_parts[*]}")"
  export PYTHONDONTWRITEBYTECODE=1
  if [[ ${#mypypath_parts[@]} -gt 0 ]]; then
    export MYPYPATH
    MYPYPATH="$(IFS=:; echo "${mypypath_parts[*]}")"
  else
    unset MYPYPATH
  fi

  echo "==> pytest (${impl}, ${#test_dirs[@]} suite(s))"
  uv run pytest "${common_args[@]}" -m "integration and ${impl}" "${test_dirs[@]}"
}

failures=0
while IFS= read -r impl; do
  if ! run_batched_impl_tests "${impl}"; then
    failures=$((failures + 1))
  fi
done < <(resolve_python_impls)

if [[ ${failures} -gt 0 ]]; then
  echo "${failures} impl batch(es) failed" >&2
  exit 1
fi
