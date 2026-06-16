#!/usr/bin/env bash
# Regenerate Smithplates artifacts via the Smithy CLI (consumer path).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
example="${repo_root}/example/python"
plugin_prefix="source/smithplates/"

cd "${repo_root}"

if ! command -v sbtn >/dev/null 2>&1; then
  echo "error: sbtn not on PATH (run publishM2 from the Smithplates repo root)" >&2
  exit 1
fi

if ! command -v smithy >/dev/null 2>&1; then
  echo "error: smithy CLI not on PATH (see docs/contributing/getting-started.md)" >&2
  exit 1
fi

sbtn publishM2
"${example}/render-smithy-build.sh"

sync_smithplates_output() {
  local build_root=$1
  local dest_root=$2
  local plugin_root="${build_root}/${plugin_prefix}"
  if [[ ! -d "${plugin_root}" ]]; then
    echo "error: missing Smithplates build output at ${plugin_root}" >&2
    exit 1
  fi
  find "${plugin_root}" -type f -print0 | while IFS= read -r -d '' file; do
    local relative="${file#"${plugin_root}"}"
    local target="${dest_root}/${relative}"
    mkdir -p "$(dirname "${target}")"
    cp "${file}" "${target}"
  done
}

run_ruff_format() {
  local format_root=$1
  if [[ ! -d "${format_root}" ]]; then
    return 0
  fi
  if command -v uv >/dev/null 2>&1 && [[ -f "${repo_root}/language-test-harnesses/python/pyproject.toml" ]]; then
    (
      cd "${repo_root}/language-test-harnesses/python"
      uv run ruff format "${format_root}" >/dev/null 2>&1 || true
    )
  fi
}

echo "Running smithy build (SQL + HTTP server/client codegen)..."
(
  cd "${example}"
  smithy build
)
sync_smithplates_output "${example}/build/smithy" "${example}"

run_ruff_format "${example}/src/generated"
run_ruff_format "${example}/tests"

echo "Generated Smithplates artifacts under ${example}"
