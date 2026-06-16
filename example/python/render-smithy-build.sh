#!/usr/bin/env bash
# Render smithy-build.json files from templates using the current Smithplates build version.
set -euo pipefail

example_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${example_root}/../.." && pwd)"

if ! command -v sbtn >/dev/null 2>&1; then
  echo "error: sbtn not on PATH (required to resolve smithplatesPlugin/version)" >&2
  exit 1
fi

SMITHPLATES_VERSION_OUTPUT="$(sbtn --no-colors 'print smithplatesPlugin/version' 2>/dev/null | tr -d '\r' || true)"
SMITHPLATES_VERSION="$(
  printf '%s\n' "${SMITHPLATES_VERSION_OUTPUT}" \
    | awk '/^[0-9]+([.][0-9]+){1,2}([-+][[:alnum:]._+-]+)?$/ { version = $0 } END { print version }'
)"
if [[ -z "${SMITHPLATES_VERSION}" ]]; then
  echo "error: could not resolve smithplatesPlugin/version via sbtn" >&2
  exit 1
fi
SMITHY_VERSION="$(
  sed -n 's/^val smithyVersion = "\(.*\)"/\1/p' "${repo_root}/build.sbt" | head -n 1
)"
if [[ -z "${SMITHY_VERSION}" ]]; then
  echo "error: could not read smithyVersion from ${repo_root}/build.sbt" >&2
  exit 1
fi

export SMITHPLATES_VERSION SMITHY_VERSION

render() {
  local template=$1
  local output=$2
  envsubst '${SMITHPLATES_VERSION} ${SMITHY_VERSION}' < "${template}" > "${output}"
  echo "rendered ${output} (smithplates ${SMITHPLATES_VERSION})"
}

render "${example_root}/smithy-build.json.template" "${example_root}/smithy-build.json"
render "${example_root}/openapi/smithy-build.json.template" "${example_root}/openapi/smithy-build.json"
