#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

ensure_example_env
cd "${EXAMPLE_ROOT}"

mapfile -t ts_files < <(collect_example_typescript_files)
if [[ ${#ts_files[@]} -eq 0 ]]; then
  echo "error: no TypeScript files found under ${EXAMPLE_ROOT}" >&2
  exit 1
fi

# Run tsc against tsconfig.json (which sets baseUrl, paths, lib, esModuleInterop,
# etc.) rather than passing input files explicitly: when input files are passed
# on the command line, tsc ignores tsconfig.json and drops those options.
echo "==> example/typescript tsc --noEmit"
npx tsc --noEmit
