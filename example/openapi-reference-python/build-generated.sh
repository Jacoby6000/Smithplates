#!/usr/bin/env bash
# Regenerate OpenAPI export and OpenAPI Generator Python client (reference implementation).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
example="${repo_root}/example/openapi-reference-python"

cd "${repo_root}"

if ! command -v sbtn >/dev/null 2>&1; then
  echo "error: sbtn not on PATH (run publishM2 from the Smithplates repo root)" >&2
  exit 1
fi

sbtn publishM2
"${example}/render-smithy-build.sh"
sbtn 'smithplatesPlugin/Test/runMain com.jacoby6000.smithplates.plugin.generators.ExampleOpenApiReferencePythonBuild' "${example}"

echo "Generated OpenAPI reference client under ${example}"
