#!/usr/bin/env bash
# Build and run SmithyStache tests inside the Nix-based Docker image.
# Requires Docker on the host with a running daemon (for testcontainers).
#
#   scripts/docker-test.sh [run-tests.sh args...]
#
# Set SMITHYSTACHE_TEST_IMAGE to override the local image tag.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

IMAGE="${SMITHYSTACHE_TEST_IMAGE:-smithystache-test:local}"

echo "Building Docker test image (${IMAGE})..."
echo "The first build can take several minutes while Nix downloads dependencies; later builds reuse cached layers and are much faster."

docker build -t "$IMAGE" -f Dockerfile .

docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "$IMAGE" \
  ./scripts/run-tests.sh "${@:-all}"
