#!/usr/bin/env bash
# Regenerate example reference project artifacts.
#
# Usage:
#   scripts/run-example-build.sh all
#   scripts/run-example-build.sh python
#   scripts/run-example-build.sh openapi-reference-python
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"

OPENAPI_GENERATOR_VERSION="7.12.0"

usage() {
  cat <<'EOF' >&2
usage: scripts/run-example-build.sh all
       scripts/run-example-build.sh python
       scripts/run-example-build.sh openapi-reference-python
EOF
  exit 2
}

require_sbtn() {
  if ! command -v sbtn >/dev/null 2>&1; then
    echo "error: sbtn not on PATH (run from the Smithplates repo root dev shell)" >&2
    exit 1
  fi
}

require_smithy() {
  if ! command -v smithy >/dev/null 2>&1; then
    echo "error: smithy CLI not on PATH (see docs/contributing/getting-started.md)" >&2
    exit 1
  fi
}

format_generated_python() {
  local example_dir=$1
  shift
  local path

  if [[ ! -f "${example_dir}/pyproject.toml" ]]; then
    return 0
  fi

  (
    cd "${example_dir}"
    uv sync
    for path in "$@"; do
      if [[ -d "${path}" ]]; then
        echo "==> ${example_dir#"${ROOT}/"} ruff format ${path}"
        uv run --group dev ruff format --config pyproject.toml "${path}"
      fi
    done
  )
}

sync_smithplates_output() {
  local example_dir=$1
  local build_root="${example_dir}/build/smithy/source/smithplates"

  if [[ ! -d "${build_root}" ]]; then
    echo "error: missing Smithplates build output at ${build_root}" >&2
    exit 1
  fi

  cp -a "${build_root}/." "${example_dir}/"
}

build_python_example() {
  local example_dir="${ROOT}/example/python"
  require_smithy

  echo "==> example/python smithy build"
  (
    cd "${example_dir}"
    smithy build
  )
  sync_smithplates_output "${example_dir}"
  format_generated_python "${example_dir}" src/generated tests
}

resolve_openapi_generator_jar() {
  local cache_directory="${XDG_CACHE_HOME:-${HOME}/.cache}/smithystache"
  local jar_path="${cache_directory}/openapi-generator-cli-${OPENAPI_GENERATOR_VERSION}.jar"

  mkdir -p "${cache_directory}"
  if [[ ! -f "${jar_path}" ]]; then
    local download_url="https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/${OPENAPI_GENERATOR_VERSION}/openapi-generator-cli-${OPENAPI_GENERATOR_VERSION}.jar"
    echo "==> downloading OpenAPI Generator ${OPENAPI_GENERATOR_VERSION}"
    curl -fsSL "${download_url}" -o "${jar_path}"
  fi
  printf '%s' "${jar_path}"
}

find_openapi_spec() {
  local example_dir=$1
  local search_root=""
  local match=""

  for search_root in "${example_dir}/build/smithy" "${example_dir}/out"; do
    if [[ -d "${search_root}" ]]; then
      match="$(
        find "${search_root}" -type f \( -name '*.openapi.json' -o -name 'openapi.json' -o -name 'openapi.yaml' \) \
          | head -n 1
      )"
      if [[ -n "${match}" ]]; then
        printf '%s' "${match}"
        return 0
      fi
    fi
  done

  echo "error: no OpenAPI spec found under ${example_dir}/build/smithy or ${example_dir}/out" >&2
  exit 1
}

build_openapi_reference_example() {
  local example_dir="${ROOT}/example/openapi-reference-python"
  require_smithy

  echo "==> example/openapi-reference-python smithy build (OpenAPI projection)"
  (
    cd "${example_dir}"
    smithy build
  )

  local open_api_spec
  open_api_spec="$(find_openapi_spec "${example_dir}")"
  local target_spec="${example_dir}/openapi/openapi.json"
  mkdir -p "$(dirname "${target_spec}")"
  cp "${open_api_spec}" "${target_spec}"

  local generator_jar
  generator_jar="$(resolve_openapi_generator_jar)"
  local client_output="${example_dir}/src/generated/client"
  rm -rf "${client_output}"
  mkdir -p "${client_output}"

  echo "==> example/openapi-reference-python OpenAPI Generator client"
  java -jar "${generator_jar}" generate \
    -i "${target_spec}" \
    -g python \
    -o "${client_output}" \
    --package-name petstore_client \
    --additional-properties generateSourceCodeOnly=true,library=asyncio,hideGenerationTimestamp=true

  format_generated_python "${example_dir}" src/generated/client/petstore_client
}

if [[ "${SMITHYSTACHE_EXAMPLE_BUILD_DONE:-}" == "1" ]]; then
  echo "==> example build skipped (already completed)"
  exit 0
fi

if [[ $# -ne 1 ]]; then
  usage
fi

require_sbtn
echo "==> publishM2"
sbtn publishM2

case "$1" in
  all)
    "${ROOT}/scripts/render-smithy-build.sh" all
    build_python_example
    build_openapi_reference_example
    ;;
  python)
    "${ROOT}/scripts/render-smithy-build.sh" example/python
    build_python_example
    ;;
  openapi-reference-python)
    "${ROOT}/scripts/render-smithy-build.sh" example/openapi-reference-python
    build_openapi_reference_example
    ;;
  *)
    usage
    ;;
esac

export SMITHYSTACHE_EXAMPLE_BUILD_DONE=1
