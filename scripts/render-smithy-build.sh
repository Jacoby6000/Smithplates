#!/usr/bin/env bash
# Render smithy-build.json from templates for example consumer projects.
#
# Usage:
#   scripts/render-smithy-build.sh all
#   scripts/render-smithy-build.sh example/python [example/openapi-reference-python ...]
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"

# shellcheck source=lib/resolve-smithy-build-versions.sh
source "${ROOT}/scripts/lib/resolve-smithy-build-versions.sh" "${ROOT}"

usage() {
  cat <<'EOF' >&2
usage: scripts/render-smithy-build.sh all
       scripts/render-smithy-build.sh <example-dir> [<example-dir> ...]
EOF
  exit 2
}

render_example() {
  local example_dir=$1
  if [[ ! -d "${example_dir}" ]]; then
    echo "error: example directory not found: ${example_dir}" >&2
    exit 1
  fi

  local template="${example_dir}/smithy-build.json.template"
  local output="${example_dir}/smithy-build.json"
  if [[ ! -f "${template}" ]]; then
    echo "error: missing smithy-build.json.template in ${example_dir}" >&2
    exit 1
  fi

  envsubst '${SMITHPLATES_VERSION} ${SMITHY_VERSION} ${SMITHPLATES_LOCAL_MAVEN_REPOSITORY_URL}' < "${template}" > "${output}"
  echo "rendered ${output} (smithplates ${SMITHPLATES_VERSION})"
}

if [[ $# -eq 0 ]]; then
  usage
fi

if [[ "$1" == "all" ]]; then
  if [[ $# -ne 1 ]]; then
    usage
  fi
  render_example "${ROOT}/example/python"
  render_example "${ROOT}/example/openapi-reference-python"
else
  for example_dir in "$@"; do
    if [[ "${example_dir}" != /* ]]; then
      example_dir="${ROOT}/${example_dir}"
    fi
    render_example "${example_dir}"
  done
fi
