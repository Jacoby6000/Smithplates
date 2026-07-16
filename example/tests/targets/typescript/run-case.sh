#!/usr/bin/env bash
# shellcheck shell=bash
set -euo pipefail

TARGET_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXAMPLE_TYPESCRIPT="$(cd "${TARGET_DIR}/../../../typescript" && pwd)"

if [[ $# -ne 2 ]]; then
  echo "usage: run-case.sh <case-file> <server-context-file>" >&2
  exit 1
fi

CASE_FILE="$1"
CONTEXT_FILE="$2"

BASE_URL="$(
  node -e "console.log(JSON.parse(require('fs').readFileSync('${CONTEXT_FILE}','utf8'))['base_url'])"
)"

cd "${EXAMPLE_TYPESCRIPT}"
exec npx tsx "${TARGET_DIR}/run_case.ts" "${CASE_FILE}" "${BASE_URL}" --context "${CONTEXT_FILE}"
