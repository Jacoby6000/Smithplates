#!/usr/bin/env bash
# Run pytest and shared HTTP reference tests for example/python.
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/common.sh"

export PYTHONUNBUFFERED=1

echo "==> example/python render smithy-build configs"
"${REPO_ROOT}/example/python/render-smithy-build.sh"

ensure_example_env
configure_example_env
cd "${EXAMPLE_ROOT}"

echo "==> example/python pytest (API smoke)"
uv run pytest tests/test_api.py

echo "==> example/python pytest (sqlite integration)"
uv run pytest tests/db/sqlite -m "integration and sqlite"

echo "==> example/python pytest (postgres integration)"
uv run pytest tests/db/postgres -m "integration and postgres"

echo "==> example/python shared HTTP reference tests (Smithplates client)"
"${REPO_ROOT}/example/tests/run-tests.sh" python python

echo "==> example/openapi-reference-python shared HTTP reference tests"
"${REPO_ROOT}/example/tests/run-tests.sh" openapi-reference-python python
