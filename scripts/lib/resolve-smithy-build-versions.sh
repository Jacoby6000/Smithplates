# Resolve Smithplates and Smithy versions for smithy-build.json templates.
# Usage: source "${ROOT}/scripts/lib/resolve-smithy-build-versions.sh" "${ROOT}"

_resolve_repo_root="${1:?repo root required}"

if ! command -v sbtn >/dev/null 2>&1; then
  echo "error: sbtn not on PATH (required to resolve smithplatesPlugin/version)" >&2
  exit 1
fi

SMITHPLATES_VERSION_OUTPUT="$(
  sbtn --no-colors 'print smithplatesPlugin/version' | tr -d '\r' || true
)"
SMITHPLATES_VERSION="$(
  printf '%s\n' "${SMITHPLATES_VERSION_OUTPUT}" \
    | sed 's/\x1b\[[0-9;]*[[:alpha:]]//g' \
    | awk '{
        line = $0
        sub(/^\[/, "", line)
        if (match(line, /[0-9]+(\.[0-9]+){1,2}[-+][[:alnum:]._+-]+/)) {
          version = substr(line, RSTART, RLENGTH)
        }
      } END { print version }'
)"
if [[ -z "${SMITHPLATES_VERSION}" ]]; then
  echo "error: could not resolve smithplatesPlugin/version via sbtn" >&2
  if [[ -n "${SMITHPLATES_VERSION_OUTPUT}" ]]; then
    echo "sbtn output:" >&2
    printf '%s\n' "${SMITHPLATES_VERSION_OUTPUT}" >&2
  fi
  exit 1
fi

SMITHY_VERSION="$(
  sed -n 's/^val smithyVersion = "\(.*\)"/\1/p' "${_resolve_repo_root}/build.sbt" | head -n 1
)"
if [[ -z "${SMITHY_VERSION}" ]]; then
  echo "error: could not read smithyVersion from ${_resolve_repo_root}/build.sbt" >&2
  exit 1
fi

SMITHPLATES_LOCAL_MAVEN_REPOSITORY_URL="file://${HOME}/.m2/repository"

export SMITHPLATES_VERSION SMITHY_VERSION SMITHPLATES_LOCAL_MAVEN_REPOSITORY_URL
