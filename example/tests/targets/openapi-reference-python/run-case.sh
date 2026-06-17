#!/usr/bin/env bash
# shellcheck shell=bash
set -euo pipefail

TARGET_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OPENAPI_REFERENCE_PYTHON="$(cd "${TARGET_DIR}/../../../openapi-reference-python" && pwd)"

if [[ $# -ne 2 ]]; then
  echo "usage: run-case.sh <case-file> <server-context-file>" >&2
  exit 1
fi

CASE_FILE="$1"
CONTEXT_FILE="$2"

BASE_URL="$(
  cd "${OPENAPI_REFERENCE_PYTHON}"
  uv run python -c 'import json, sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["base_url"])' "${CONTEXT_FILE}"
)"

export PYTHONPATH="${OPENAPI_REFERENCE_PYTHON}/src/generated/client${PYTHONPATH:+:${PYTHONPATH}}"

cd "${OPENAPI_REFERENCE_PYTHON}"
exec uv run python "${TARGET_DIR}/run_case.py" "${CASE_FILE}" "${BASE_URL}" --context "${CONTEXT_FILE}"
