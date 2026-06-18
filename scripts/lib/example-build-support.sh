# Shared helpers for example consumer project regeneration (bash only).
# shellcheck shell=bash

OPENAPI_GENERATOR_VERSION="${OPENAPI_GENERATOR_VERSION:-7.12.0}"
SMITHY_PLUGIN_OUTPUT_PREFIX="source/smithplates"

example_build_require_smithy() {
  if ! command -v smithy >/dev/null 2>&1; then
    echo "error: smithy not on PATH (use the Smithplates dev shell)" >&2
    exit 1
  fi
}

example_build_smithy_output_root() {
  local example_root=$1
  printf '%s/build/smithy' "${example_root}"
}

example_build_run_smithy() {
  local example_root=$1
  if [[ ! -f "${example_root}/smithy-build.json" ]]; then
    echo "error: missing smithy-build.json in ${example_root}" >&2
    exit 1
  fi
  (
    cd "${example_root}"
    smithy build
  )
}

example_build_sync_plugin_output() {
  local example_root=$1
  local plugin_output="${example_root}/build/smithy/${SMITHY_PLUGIN_OUTPUT_PREFIX}"

  if [[ ! -d "${plugin_output}" ]]; then
    echo "error: missing Smithy plugin output at ${plugin_output}" >&2
    exit 1
  fi

  find "${plugin_output}" -type f -print0 | while IFS= read -r -d '' source_file; do
    local rel="${source_file#"${plugin_output}/"}"
    local target="${example_root}/${rel}"
    mkdir -p "$(dirname "${target}")"
    cp "${source_file}" "${target}"
  done
}

example_build_format_python() {
  local example_root=$1
  shift

  if ! command -v uv >/dev/null 2>&1; then
    echo "warning: uv not on PATH; skipping ruff format under ${example_root}" >&2
    return 0
  fi

  (
    cd "${example_root}"
    if [[ -f pyproject.toml ]]; then
      uv sync >/dev/null 2>&1 || uv sync
    fi
    local relative
    for relative in "$@"; do
      if [[ -d "${relative}" ]]; then
        echo "==> ruff format ${example_root}/${relative}"
        uv run ruff format --config pyproject.toml "${relative}" || {
          echo "warning: ruff format failed for ${example_root}/${relative}" >&2
        }
      fi
    done
  )
}

example_build_resolve_openapi_generator_jar() {
  local cache_directory="${XDG_CACHE_HOME:-${HOME}/.cache}/smithystache"
  mkdir -p "${cache_directory}"
  local jar_path="${cache_directory}/openapi-generator-cli-${OPENAPI_GENERATOR_VERSION}.jar"
  if [[ ! -f "${jar_path}" ]]; then
    local download_url="https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/${OPENAPI_GENERATOR_VERSION}/openapi-generator-cli-${OPENAPI_GENERATOR_VERSION}.jar"
    echo "==> downloading OpenAPI Generator CLI ${OPENAPI_GENERATOR_VERSION}"
    curl -fsSL "${download_url}" -o "${jar_path}"
  fi
  printf '%s' "${jar_path}"
}

example_build_find_openapi_spec() {
  local search_root=$1
  local candidate
  while IFS= read -r candidate; do
    printf '%s' "${candidate}"
    return 0
  done < <(
    find "${search_root}" -type f \( \
      -iname '*.openapi.json' \
      -o -iname 'openapi.json' \
      -o -iname 'openapi.yaml' \
      \) -print 2>/dev/null | head -n 1
  )
  echo "error: no OpenAPI spec found under ${search_root}" >&2
  return 1
}

example_build_generate_openapi_python_client() {
  local example_root=$1
  local open_api_spec=$2
  local client_output="${example_root}/src/generated/client"
  local generator_jar
  generator_jar="$(example_build_resolve_openapi_generator_jar)"

  rm -rf "${client_output}"
  mkdir -p "${client_output}"

  echo "==> OpenAPI Generator python client -> ${client_output}"
  java -jar "${generator_jar}" generate \
    -i "${open_api_spec}" \
    -g python \
    -o "${client_output}" \
    --package-name petstore_client \
    --additional-properties generateSourceCodeOnly=true,library=asyncio,hideGenerationTimestamp=true
}
