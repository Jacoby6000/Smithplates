#!/usr/bin/env bash
# Load a persisted Docker test image archive from GitHub Actions cache.
set -euo pipefail

CACHE_DIR="${SMITHYSTACHE_DOCKER_EXPORT_DIR:-/tmp/docker-image-cache}"
ARCHIVE_GZ="${CACHE_DIR}/smithystache-test-image.tar.gz"
ARCHIVE_TAR="${CACHE_DIR}/smithystache-test-image.tar"

if [[ -s "${ARCHIVE_GZ}" ]]; then
  echo "Loading Docker test image from ${ARCHIVE_GZ}"
  gzip -dc "${ARCHIVE_GZ}" | docker load
elif [[ -s "${ARCHIVE_TAR}" ]]; then
  echo "Loading Docker test image from ${ARCHIVE_TAR}"
  docker load -i "${ARCHIVE_TAR}"
else
  echo "No Docker test image archive found under ${CACHE_DIR}"
  exit 1
fi
