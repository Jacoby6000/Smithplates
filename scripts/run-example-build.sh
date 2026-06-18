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

# shellcheck source=lib/publish-m2-once.sh
source "${ROOT}/scripts/lib/publish-m2-once.sh"
# shellcheck source=lib/example-build-support.sh
source "${ROOT}/scripts/lib/example-build-support.sh"

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

build_python_example() {
  local example_root="${ROOT}/example/python"
  echo "==> example/python smithy build"
  example_build_run_smithy "${example_root}"
  example_build_sync_plugin_output "${example_root}"
  example_build_format_python "${example_root}" src/generated tests
  echo "Generated Smithplates artifacts under ${example_root}"
}

build_openapi_reference_example() {
  local example_root="${ROOT}/example/openapi-reference-python"
  echo "==> example/openapi-reference-python smithy build (OpenAPI projection)"
  example_build_run_smithy "${example_root}"

  local smithy_output
  smithy_output="$(example_build_smithy_output_root "${example_root}")"
  local source_spec
  source_spec="$(example_build_find_openapi_spec "${smithy_output}")"
  local target_spec="${example_root}/openapi/openapi.json"
  mkdir -p "$(dirname "${target_spec}")"
  cp "${source_spec}" "${target_spec}"

  example_build_generate_openapi_python_client "${example_root}" "${target_spec}"
  example_build_format_python "${example_root}" src/generated/client/petstore_client
  echo "Generated OpenAPI reference client under ${example_root}"
}

if [[ "${SMITHYSTACHE_EXAMPLE_BUILD_DONE:-}" == "1" ]]; then
  echo "==> example build skipped (already completed)"
  exit 0
fi

if [[ $# -ne 1 ]]; then
  usage
fi

enter_nix_shell_if_available "$@"

example_build_require_smithy
smithystache_publish_m2_once

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
