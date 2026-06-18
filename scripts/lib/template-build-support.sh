# Smithy CLI build helpers for golden template fixtures.
# shellcheck shell=bash

# shellcheck source=example-build-support.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/example-build-support.sh"

template_build_render_smithy_config() {
  local case_dir=$1
  local repo_root=$2
  local template="${case_dir}/smithy-build.json.template"
  local output="${case_dir}/smithy-build.json"

  if [[ ! -f "${template}" ]]; then
    echo "error: missing ${template}" >&2
    return 1
  fi

  # shellcheck source=resolve-smithy-build-versions.sh
  source "${repo_root}/scripts/lib/resolve-smithy-build-versions.sh" "${repo_root}"
  envsubst '${SMITHPLATES_VERSION}' < "${template}" > "${output}"
}

template_build_run() {
  local case_dir=$1
  local repo_root=$2

  template_build_render_smithy_config "${case_dir}" "${repo_root}"
  example_build_require_smithy
  example_build_run_smithy "${case_dir}"
  printf '%s/build/smithy\n' "${case_dir}"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  set -euo pipefail
  if [[ $# -ne 3 || "$1" != "build" ]]; then
    echo "usage: ${0} build <case-directory> <repo-root>" >&2
    exit 2
  fi
  template_build_run "$2" "$3"
fi
