#!/usr/bin/env bash
# Load a persisted Docker test image archive from GitHub Actions cache.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=scripts/lib/docker-image.sh
source "${ROOT}/scripts/lib/docker-image.sh"

CACHE_DIR="${SMITHYSTACHE_DOCKER_EXPORT_DIR:-/tmp/docker-image-cache}"
ARCHIVE_GZ="${CACHE_DIR}/smithystache-test-image.tar.gz"
METADATA="${CACHE_DIR}/smithystache-test-image.metadata"

cache_miss() {
  echo "Docker image cache not reusable ($1); validate will rebuild."
}
if ! smithystache_docker_marker_path_safe; then
  cache_miss 'unsafe marker path'
  exit 0
fi
# A rejected restore must not leave an older marker authorizing the wrong image.
rm -f "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}"
declare -a metadata_fields=()
if [[ -L "$CACHE_DIR" ]] || ! smithystache_docker_read_cache_metadata "$ARCHIVE_GZ" "$METADATA" metadata_fields; then
  cache_miss 'missing, stale, or invalid metadata/archive'
  exit 0
fi
echo "Loading Docker test image from ${ARCHIVE_GZ}"
if ! load_output="$(gzip -dc "$ARCHIVE_GZ" | docker load)"; then
  cache_miss 'archive load failed'
  exit 0
fi
# Export saves by immutable ID, so Docker reports the loaded ID. Check that the
# load actually supplied it, rather than accepting a preexisting colliding tag.
if ! grep -Fxq "Loaded image ID: ${metadata_fields[2]}" <<< "$load_output" ||
   [[ "$(docker image inspect --format '{{.Id}}' "${metadata_fields[2]}")" != "${metadata_fields[2]}" ]] ||
   [[ "$(docker image inspect --format '{{.Os}}/{{.Architecture}}{{if .Variant}}/{{.Variant}}{{end}}' "${metadata_fields[2]}")" != "$SMITHYSTACHE_DOCKER_PLATFORM" ]]; then
  cache_miss 'loaded image identity/platform mismatch'
  exit 0
fi
docker tag "${metadata_fields[2]}" "$SMITHYSTACHE_TEST_IMAGE"
if ! smithystache_docker_image_identity_matches "${metadata_fields[2]}"; then
  cache_miss 'tag identity mismatch'
  exit 0
fi
smithystache_docker_write_input_marker "${metadata_fields[1]}" "${metadata_fields[2]}"
echo 'Restored verified Docker image input marker.'
