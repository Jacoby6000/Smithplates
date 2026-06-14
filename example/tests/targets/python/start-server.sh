#!/usr/bin/env bash
# shellcheck shell=bash
set -euo pipefail

TARGET_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXAMPLE_PYTHON="$(cd "${TARGET_DIR}/../../../python" && pwd)"

if [[ $# -ne 2 ]]; then
  echo "usage: start-server.sh <context-file> <pid-file>" >&2
  exit 1
fi

CONTEXT_FILE="$1"
PID_FILE="$2"

cd "${EXAMPLE_PYTHON}"
exec uv run python "${TARGET_DIR}/start_server.py" "${CONTEXT_FILE}" "${PID_FILE}"
