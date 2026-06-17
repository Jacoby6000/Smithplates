# Publish Smithplates plugin artifacts to ~/.m2 once per shell session.
# Usage: source "${ROOT}/scripts/lib/publish-m2-once.sh"
#        smithystache_publish_m2_once

smithystache_publish_m2_once() {
  if [[ "${SMITHYSTACHE_PUBLISH_M2_DONE:-}" == "1" ]]; then
    echo "==> publishM2 skipped (already completed)"
    return 0
  fi

  if ! command -v sbtn >/dev/null 2>&1; then
    echo "error: sbtn not on PATH (required for publishM2)" >&2
    return 1
  fi

  echo "==> publishM2"
  sbtn publishM2
  export SMITHYSTACHE_PUBLISH_M2_DONE=1
}
