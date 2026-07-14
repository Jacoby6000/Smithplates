#!/usr/bin/env bash
# Run tests for example/typescript.
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

ensure_example_env
cd "${EXAMPLE_ROOT}"

echo "==> example/typescript typecheck"
npx tsc --noEmit

echo "==> example/typescript tests"
npx tsx --test tests/
