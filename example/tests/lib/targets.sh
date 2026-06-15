#!/usr/bin/env bash
# shellcheck shell=bash

TESTS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "${TESTS_ROOT}/../.." && pwd)"

resolve_target_dir() {
  local target_name="$1"
  local target_dir="${TESTS_ROOT}/targets/${target_name}"

  if [[ ! -d "${target_dir}" ]]; then
    echo "error: unknown language target '${target_name}' (missing ${target_dir})" >&2
    return 1
  fi

  if [[ ! -x "${target_dir}/start-server.sh" ]] || [[ ! -x "${target_dir}/run-case.sh" ]]; then
    echo "error: target '${target_name}' must provide executable start-server.sh and run-case.sh" >&2
    return 1
  fi

  printf '%s\n' "${target_dir}"
}

list_case_files() {
  local cases_root="${TESTS_ROOT}/cases"
  find "${cases_root}" -maxdepth 1 -type f -name '*.case.json' | sort
}

case_name_from_path() {
  basename "$1" .case.json
}

filter_case_files() {
  local -a selected=("$@")
  local -a filters=()
  local case_file case_name

  if [[ ${#selected[@]} -gt 0 ]]; then
    filters=("${selected[@]}")
  fi

  while IFS= read -r case_file; do
    case_name="$(case_name_from_path "${case_file}")"
    if [[ ${#filters[@]} -eq 0 ]]; then
      printf '%s\n' "${case_file}"
      continue
    fi
    for filter in "${filters[@]}"; do
      if [[ "${case_name}" == "${filter}" ]]; then
        printf '%s\n' "${case_file}"
        break
      fi
    done
  done < <(list_case_files)
}
