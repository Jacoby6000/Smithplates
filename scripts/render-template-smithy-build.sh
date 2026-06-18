#!/usr/bin/env bash
# Render smithy-build.json from templates for golden template fixtures.
#
# Usage:
#   scripts/render-template-smithy-build.sh all
#   scripts/render-template-smithy-build.sh templates/python/tests/<case-name>
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "${ROOT}"

# shellcheck source=lib/resolve-smithy-build-versions.sh
source "${ROOT}/scripts/lib/resolve-smithy-build-versions.sh" "${ROOT}"

usage() {
  cat <<'EOF' >&2
usage: scripts/render-template-smithy-build.sh all
       scripts/render-template-smithy-build.sh <case-directory> [<case-directory> ...]
EOF
  exit 2
}

render_case() {
  local case_dir=$1
  if [[ ! -d "${case_dir}" ]]; then
    echo "error: case directory not found: ${case_dir}" >&2
    exit 1
  fi

  local template="${case_dir}/smithy-build.json.template"
  local output="${case_dir}/smithy-build.json"
  if [[ ! -f "${template}" ]]; then
    echo "error: missing smithy-build.json.template in ${case_dir}" >&2
    exit 1
  fi

  envsubst '${SMITHPLATES_VERSION}' < "${template}" > "${output}"
  echo "rendered ${output} (smithplates ${SMITHPLATES_VERSION})"
}

if [[ $# -eq 0 ]]; then
  usage
fi

if [[ "$1" == "all" ]]; then
  if [[ $# -ne 1 ]]; then
    usage
  fi
  shopt -s nullglob
  for template in templates/*/tests/*/smithy-build.json.template; do
    render_case "$(dirname "${template}")"
  done
else
  for case_dir in "$@"; do
    if [[ "${case_dir}" != /* ]]; then
      case_dir="${ROOT}/${case_dir}"
    fi
    render_case "${case_dir}"
  done
fi
