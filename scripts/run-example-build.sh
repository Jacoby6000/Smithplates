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

enter_nix_shell_if_available() {
  if [[ -n "${IN_NIX_SHELL:-}" ]]; then
    return 0
  fi
  if ! command -v nix >/dev/null 2>&1 || [[ ! -f "${ROOT}/flake.nix" ]]; then
    return 0
  fi

  exec nix develop "${ROOT}" --accept-flake-config --command "$0" "$@"
}

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

require_java() {
  if ! command -v java >/dev/null 2>&1; then
    echo "error: java not on PATH (required to run Smithy with the local plugin classpath)" >&2
    exit 1
  fi
}

require_coursier() {
  if ! command -v coursier >/dev/null 2>&1; then
    echo "error: coursier not on PATH (required to resolve locally published Smithplates jars)" >&2
    exit 1
  fi
}

local_smithy_extension_classpath() {
  coursier fetch --classpath \
    --repository "${SMITHPLATES_LOCAL_MAVEN_REPOSITORY_URL}" \
    "com.jacoby6000:smithplates-plugin:${SMITHPLATES_VERSION}" \
    "software.amazon.smithy:smithy-aws-traits:${SMITHY_VERSION}" \
    "software.amazon.smithy:smithy-openapi:${SMITHY_VERSION}"
}

run_smithy_build_with_local_plugin() {
  local extension_classpath
  local smithy_classpath

  extension_classpath="$(local_smithy_extension_classpath)"
  smithy_classpath="$(coursier fetch --classpath "software.amazon.smithy:smithy-cli:${SMITHY_VERSION}")"

  SMITHY_DEPENDENCY_MODE=ignore java \
    -cp "${smithy_classpath}:${extension_classpath}" \
    software.amazon.smithy.cli.SmithyCli \
    build \
    --discover \
    --discover-classpath "${extension_classpath}"
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
        echo "==> ${example_dir#"${ROOT}/"} ruff check --fix ${path}"
        uv run --group dev ruff check --fix --config pyproject.toml "${path}"
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
  require_java

  echo "==> example/python smithy build"
  (
    cd "${example_dir}"
    run_smithy_build_with_local_plugin
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
  require_java

  echo "==> example/openapi-reference-python smithy build (OpenAPI projection)"
  (
    cd "${example_dir}"
    run_smithy_build_with_local_plugin
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
}

if [[ "${SMITHYSTACHE_EXAMPLE_BUILD_DONE:-}" == "1" ]]; then
  echo "==> example build skipped (already completed)"
  exit 0
fi

if [[ $# -ne 1 ]]; then
  usage
fi

enter_nix_shell_if_available "$@"

require_sbtn
require_coursier
echo "==> publishM2"
sbtn publishM2

# shellcheck source=lib/resolve-smithy-build-versions.sh
source "${ROOT}/scripts/lib/resolve-smithy-build-versions.sh" "${ROOT}"

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
