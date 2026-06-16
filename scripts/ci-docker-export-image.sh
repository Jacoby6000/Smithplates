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
ARCHIVE_TAR="${CACHE_DIR}/smithystache-test-image.tar"
BUILT_MARKER="${SMITHYSTACHE_DOCKER_CACHE_DIR}/built-this-run"

mkdir -p "${CACHE_DIR}"

if ! smithystache_docker_image_exists; then
  echo "No Docker test image to export."
  exit 0
fi

if [[ ! -f "${BUILT_MARKER}" ]] && [[ -s "${ARCHIVE_GZ}" || -s "${ARCHIVE_TAR}" ]]; then
  echo "Skipping Docker image export; restored cache archive is still valid."
  exit 0
fi

echo "==> Freeing disk space before Docker image export"
if command -v nix-collect-garbage >/dev/null 2>&1; then
  nix-collect-garbage -d || true
fi
docker builder prune -af >/dev/null 2>&1 || true

rm -f "${ARCHIVE_TAR}" "${ARCHIVE_GZ}"
echo "==> Exporting Docker test image to ${ARCHIVE_GZ}"
docker save "${SMITHYSTACHE_TEST_IMAGE}" | gzip -1 > "${ARCHIVE_GZ}"
