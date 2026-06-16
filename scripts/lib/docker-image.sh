# Shared Docker image build/cache helpers for validate backends.
# Requires ROOT (repository root) and set -euo pipefail in the caller.

SMITHYSTACHE_TEST_IMAGE="${SMITHYSTACHE_TEST_IMAGE:-smithystache-test:local}"
SMITHYSTACHE_DOCKER_PLATFORM="${SMITHYSTACHE_DOCKER_PLATFORM:-linux/amd64}"
SMITHYSTACHE_DOCKER_CACHE_DIR="${ROOT}/target/docker-test"
SMITHYSTACHE_DOCKER_INPUT_HASH_FILE="${SMITHYSTACHE_DOCKER_CACHE_DIR}/image-input.hash"

smithystache_docker_image_input_hash() {
  sha256sum Dockerfile flake.nix flake.lock | sha256sum | awk '{print $1}'
}

smithystache_docker_image_exists() {
  docker image inspect "${SMITHYSTACHE_TEST_IMAGE}" >/dev/null 2>&1
}

smithystache_docker_append_ci_cache_mounts() {
  local -n out_args=$1
  if [[ -z "${SMITHYSTACHE_DOCKER_CI_CACHE:-}" ]]; then
    return 0
  fi

  local cache_root="${ROOT}/.ci-cache"
  mkdir -p \
    "${cache_root}/coursier" \
    "${cache_root}/sbt" \
    "${cache_root}/ivy2/cache" \
    "${cache_root}/uv"
  out_args+=(
    -v "${cache_root}/coursier:/root/.cache/coursier"
    -v "${cache_root}/sbt:/root/.sbt"
    -v "${cache_root}/ivy2:/root/.ivy2"
    -v "${cache_root}/uv:/root/.cache/uv"
  )
}

smithystache_needs_docker_build() {
  if ! smithystache_docker_image_exists; then
    return 0
  fi
  if [[ ! -f "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}" ]]; then
    return 0
  fi
  [[ "$(cat "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}")" != "$(smithystache_docker_image_input_hash)" ]]
}

smithystache_ensure_docker_image() {
  rm -f "${SMITHYSTACHE_DOCKER_CACHE_DIR}/built-this-run"
  if smithystache_needs_docker_build; then
    mkdir -p "${SMITHYSTACHE_DOCKER_CACHE_DIR}"
    echo "Building Docker test image (${SMITHYSTACHE_TEST_IMAGE})..."
    echo "The first build can take several minutes while Nix downloads dependencies; later builds reuse cached layers and are much faster."
    docker build --platform "${SMITHYSTACHE_DOCKER_PLATFORM}" -t "${SMITHYSTACHE_TEST_IMAGE}" -f Dockerfile .
    smithystache_docker_image_input_hash > "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}"
    touch "${SMITHYSTACHE_DOCKER_CACHE_DIR}/built-this-run"
  else
    echo "Reusing Docker test image (${SMITHYSTACHE_TEST_IMAGE}); rebuilds when Dockerfile or flake inputs change."
  fi
}

# Usage: smithystache_docker_run [-v ...] -- command [args...]
smithystache_docker_run() {
  local -a docker_run_args=()
  while [[ $# -gt 0 && "$1" != "--" ]]; do
    docker_run_args+=("$1")
    shift
  done
  if [[ $# -eq 0 || "$1" != "--" ]]; then
    echo "error: smithystache_docker_run requires '--' before the container command" >&2
    return 2
  fi
  shift
  local -a run_mounts=()
  smithystache_docker_append_ci_cache_mounts run_mounts
  docker run --rm \
    --platform "${SMITHYSTACHE_DOCKER_PLATFORM}" \
    "${docker_run_args[@]}" \
    "${run_mounts[@]}" \
    -v "${ROOT}:/smithystache" \
    -w /smithystache \
    "${SMITHYSTACHE_TEST_IMAGE}" \
    "$@"
}
