#!/usr/bin/env bash
set -euo pipefail

harness_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${harness_dir}/../.." && pwd)"
expected_root="${repo_root}/templates/python/expected-outputs"
basetemp="${repo_root}/target/language-test-harnesses/python/pytest-tmp"

mkdir -p "${basetemp}"
cd "${harness_dir}"
uv sync

common_args=(
  -c "${harness_dir}/pyproject.toml"
  --basetemp="${basetemp}"
  -p no:cacheprovider
)

if [[ $# -gt 0 ]]; then
  exec uv run pytest "${common_args[@]}" "$@"
fi

collect_python_files() {
  local db_root="$1"
  local impl="$2"
  local test_dir="$3"

  if [[ -d "${db_root}/model" ]]; then
    find "${db_root}/model" -name '*.py' -type f
  fi
  find "${db_root}" -maxdepth 1 -name '*.py' -type f
  if [[ -d "${db_root}/${impl}" ]]; then
    find "${db_root}/${impl}" -name '*.py' -type f
  fi
  find "${test_dir}" -maxdepth 1 -name 'test_*.py' -type f
}

run_variant_checks() {
  local case_name="$1"
  local impl="$2"
  local db_root="$3"
  local test_dir="$4"
  local label="${case_name} / ${impl}"

  mapfile -t python_files < <(collect_python_files "${db_root}" "${impl}" "${test_dir}")
  if [[ ${#python_files[@]} -eq 0 ]]; then
    echo "no Python files to check for ${label}" >&2
    return 1
  fi

  export PYTHONPATH="${db_root}/model:${db_root}:${db_root}/${impl}"
  export PYTHONDONTWRITEBYTECODE=1
  mypy_path="${PYTHONPATH}"
  if [[ -d "${test_dir}/stubs" ]]; then
    mypy_path="${test_dir}/stubs:${mypy_path}"
  fi
  export MYPYPATH="${mypy_path}"

  echo "==> ${label} ruff check"
  uv run ruff check --config "${harness_dir}/pyproject.toml" "${python_files[@]}" || return 1

  echo "==> ${label} ruff format --check"
  uv run ruff format --check --config "${harness_dir}/pyproject.toml" "${python_files[@]}" || return 1

  echo "==> ${label} mypy"
  uv run mypy --strict --config-file "${harness_dir}/pyproject.toml" "${python_files[@]}" || return 1

  echo "==> ${label} pytest"
  uv run pytest "${common_args[@]}" -m "integration and ${impl}" "${test_dir}" || return 1
}

shopt -s nullglob
failures=0

for case_dir in "${expected_root}"/*/; do
  case_name="$(basename "${case_dir}")"
  db_root="${case_dir}src/db"

  for impl in sqlite postgres; do
    test_dir="${case_dir}test/db/${impl}"
    unsupported="${db_root}/${impl}/unsupported.md"

    [[ -d "${test_dir}" ]] || continue
    [[ -f "${unsupported}" ]] && continue
    compgen -G "${test_dir}/test_*_derived_sql.py" >/dev/null || continue

    if ! run_variant_checks "${case_name}" "${impl}" "${db_root}" "${test_dir}"; then
      failures=$((failures + 1))
    fi
  done
done

if [[ ${failures} -gt 0 ]]; then
  echo "${failures} case variant(s) failed" >&2
  exit 1
fi
