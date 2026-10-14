#!/usr/bin/env bash
# Export the Docker test image for GitHub Actions cache persistence.
# Skips re-export when the restored archive is still valid to avoid duplicating
# the image on disk (docker save needs roughly one image worth of free space).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

# shellcheck source=scripts/lib/docker-image.sh
source "${ROOT}/scripts/lib/docker-image.sh"

CACHE_DIR="${SMITHYSTACHE_DOCKER_EXPORT_DIR:-/tmp/docker-image-cache}"
ARCHIVE_GZ="${CACHE_DIR}/smithystache-test-image.tar.gz"
METADATA="${CACHE_DIR}/smithystache-test-image.metadata"

[[ ! -L "${CACHE_DIR}" && ! -L "${ARCHIVE_GZ}" && ! -L "${METADATA}" ]] || {
  echo "error: unsafe Docker export path" >&2; exit 1;
}
mkdir -p "${CACHE_DIR}"

if ! smithystache_docker_image_exists; then
  echo "No Docker test image to export."
  exit 0
fi

# Only a producer marker from ensure-image (or a verified restore) authorizes
# export. Never infer that an arbitrary existing image has current inputs.
if smithystache_needs_docker_build; then
  echo "No verified current Docker test image to export."
  exit 0
fi
mapfile -t producer_marker < "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}"
INPUT_HASH="${producer_marker[0]}"
IMAGE_ID="${producer_marker[1]}"
if ! smithystache_docker_image_identity_matches "$IMAGE_ID"; then
  echo "error: Docker image changed before export" >&2; exit 1;
fi
declare -a metadata_fields=()
if smithystache_docker_read_cache_metadata "$ARCHIVE_GZ" "$METADATA" metadata_fields &&
   [[ "${metadata_fields[2]}" == "$IMAGE_ID" ]]; then
  echo "Skipping Docker image export; restored cache archive is still valid."
  exit 0
fi

echo "==> Freeing disk space before Docker image export"
if command -v nix-collect-garbage >/dev/null 2>&1; then
  nix-collect-garbage -d || true
fi
docker builder prune -af >/dev/null 2>&1 || true

echo "==> Exporting Docker test image to ${ARCHIVE_GZ}"
temporary_archive="$(mktemp "${CACHE_DIR}/image.XXXXXX")"
temporary_metadata="$(mktemp "${CACHE_DIR}/metadata.XXXXXX")"
trap 'rm -f "$temporary_archive" "$temporary_metadata"' EXIT
# Save the inspected immutable ID, not a tag that could move during export.
docker save "$IMAGE_ID" | gzip -1 > "$temporary_archive"
printf '%s\n' smithystache-docker-cache-v2 "$INPUT_HASH" "$IMAGE_ID" \
  "$SMITHYSTACHE_DOCKER_PLATFORM" "$(sha256sum "$temporary_archive" | awk '{print $1}')" > "$temporary_metadata"
mv -f "$temporary_archive" "$ARCHIVE_GZ"
mv -f "$temporary_metadata" "$METADATA"
