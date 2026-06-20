#!/usr/bin/env bash
# Remove transient smithy build output and empty stub dirs under template golden tests.
# Invoked by pre-commit, ./validate, and template golden test runs.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

for tests_root in "${ROOT}"/templates/*/tests; do
  [[ -d "${tests_root}" ]] || continue
  for case_dir in "${tests_root}"/*; do
    [[ -d "${case_dir}" ]] || continue
    rm -rf "${case_dir}/out"
    if [[ ! -f "${case_dir}/smithy/smithy-files.smithy" && ! -f "${case_dir}/smithy-build.json" ]]; then
      if [[ -z "$(ls -A "${case_dir}")" ]]; then
        rm -rf "${case_dir}"
      fi
    fi
  done
done
