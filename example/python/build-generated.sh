#!/usr/bin/env bash
# Regenerate Smithplates artifacts via the Smithy CLI (consumer path), then OpenAPI Generator.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
example="${repo_root}/example/python"
openapi_generator_version="7.12.0"
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

ensure_http_package_namespace() {
  local example_root=$1
  local namespace_root="${example_root}/src/generated/generated"
  local api_root="${example_root}/src/generated/api"
  if [[ ! -d "${api_root}" ]]; then
    return 0
  fi
  mkdir -p "${namespace_root}"
  touch "${namespace_root}/__init__.py"
  ln -sfn "$(realpath "${api_root}")" "${namespace_root}/petstore_api"
}

find_openapi_spec() {
  local search_root=$1
  find "${search_root}" -type f \( \
    -name '*.openapi.json' -o -name 'openapi.json' -o -name 'openapi.yaml' \
  \) -print -quit
}

resolve_openapi_generator_jar() {
  local cache_dir="${XDG_CACHE_HOME:-${HOME}/.cache}/smithystache"
  local jar_path="${cache_dir}/openapi-generator-cli-${openapi_generator_version}.jar"
  mkdir -p "${cache_dir}"
  if [[ ! -f "${jar_path}" ]]; then
    curl -fsSL \
      "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/${openapi_generator_version}/openapi-generator-cli-${openapi_generator_version}.jar" \
      -o "${jar_path}"
  fi
  printf '%s' "${jar_path}"
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

echo "Running smithy build (SQL + HTTP codegen)..."
(
  cd "${example}"
  smithy build
)
sync_smithplates_output "${example}/build/smithy" "${example}"
ensure_http_package_namespace "${example}"

echo "Running smithy build (OpenAPI export)..."
(
  cd "${example}/openapi"
  smithy build
)
openapi_spec="$(find_openapi_spec "${example}/openapi/build/smithy")"
if [[ -z "${openapi_spec}" ]]; then
  echo "error: no OpenAPI spec found under ${example}/openapi/build/smithy" >&2
  exit 1
fi
mkdir -p "${example}/openapi"
cp "${openapi_spec}" "${example}/openapi/openapi.json"

client_output="${example}/src/generated/client"
rm -rf "${client_output}"
mkdir -p "${client_output}"
generator_jar="$(resolve_openapi_generator_jar)"
java -jar "${generator_jar}" generate \
  -i "${example}/openapi/openapi.json" \
  -g python \
  -o "${client_output}" \
  --package-name petstore_client \
  --additional-properties generateSourceCodeOnly=true,library=asyncio,hideGenerationTimestamp=true

run_ruff_format "${example}/src/generated"
run_ruff_format "${example}/tests"
run_ruff_format "${client_output}/petstore_client"

echo "Generated Smithplates artifacts under ${example}"
