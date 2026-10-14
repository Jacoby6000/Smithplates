# Shared Docker image build/cache helpers for validate backends.
# Requires ROOT (repository root) and set -euo pipefail in the caller.
# shellcheck shell=bash

SMITHYSTACHE_TEST_IMAGE="${SMITHYSTACHE_TEST_IMAGE:-smithystache-test:local}"
SMITHYSTACHE_DOCKER_PLATFORM="${SMITHYSTACHE_DOCKER_PLATFORM:-linux/amd64}"
SMITHYSTACHE_DOCKER_CACHE_DIR="${ROOT}/target/docker-test"
SMITHYSTACHE_DOCKER_INPUT_HASH_FILE="${SMITHYSTACHE_DOCKER_CACHE_DIR}/image-input.hash"

smithystache_docker_image_input_hash() {
  # Dockerfile copies only these dev-shell inputs (see .dockerignore). Include
  # cache protocol scripts and platform so old producers cannot bless new inputs.
  {
    printf '%s\n' 'smithystache-docker-cache-v2' "${SMITHYSTACHE_DOCKER_PLATFORM}"
    sha256sum Dockerfile .dockerignore flake.nix flake.lock build.sbt \
      scripts/lib/docker-image.sh scripts/ci-docker-export-image.sh scripts/ci-docker-load-image.sh
  } | sha256sum | awk '{print $1}'
}

smithystache_docker_marker_path_safe() {
  [[ ! -L "${ROOT}/target" && ! -L "${SMITHYSTACHE_DOCKER_CACHE_DIR}" &&
     ! -L "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}" ]]
}

# Fixed, non-executable five-line metadata: schema, producer input hash, image
# ID, platform, compressed archive checksum. Paths never come from metadata.
smithystache_docker_read_cache_metadata() {
  local archive="$1" metadata="$2"
  local -n fields="$3"
  [[ -f "$archive" && -s "$archive" && ! -L "$archive" &&
     -f "$metadata" && ! -L "$metadata" ]] || return 1
  [[ "$(wc -c < "$metadata")" -le 512 ]] || return 1
  mapfile -t fields < "$metadata"
  [[ ${#fields[@]} -eq 5 ]] || return 1
  printf '%s\n' "${fields[@]}" | cmp -s - "$metadata" || return 1
  [[ "${fields[0]}" == smithystache-docker-cache-v2 &&
     "${fields[1]}" =~ ^[0-9a-f]{64}$ &&
     "${fields[2]}" =~ ^sha256:[0-9a-f]{64}$ &&
     "${fields[3]}" == "${SMITHYSTACHE_DOCKER_PLATFORM}" &&
     "${fields[4]}" =~ ^[0-9a-f]{64}$ ]] || return 1
  [[ "${fields[1]}" == "$(smithystache_docker_image_input_hash)" &&
     "${fields[4]}" == "$(sha256sum "$archive" | awk '{print $1}')" ]]
}

smithystache_docker_image_identity_matches() {
  local expected_id="$1"
  [[ "$(docker image inspect --format '{{.Id}}' "${SMITHYSTACHE_TEST_IMAGE}")" == "$expected_id" &&
     "$(docker image inspect --format '{{.Os}}/{{.Architecture}}{{if .Variant}}/{{.Variant}}{{end}}' "${SMITHYSTACHE_TEST_IMAGE}")" == "${SMITHYSTACHE_DOCKER_PLATFORM}" ]]
}

smithystache_docker_write_input_marker() {
  local input_hash="$1" image_id="$2" temporary
  smithystache_docker_marker_path_safe || return 1
  mkdir -p "${SMITHYSTACHE_DOCKER_CACHE_DIR}"
  temporary="$(mktemp "${SMITHYSTACHE_DOCKER_CACHE_DIR}/image-input.XXXXXX")"
  printf '%s\n' "$input_hash" "$image_id" "$SMITHYSTACHE_DOCKER_PLATFORM" > "$temporary"
  mv -f "$temporary" "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}"
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
  if ! smithystache_docker_marker_path_safe || [[ ! -f "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}" ]]; then
    return 0
  fi
  local -a marker=()
  [[ "$(wc -c < "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}")" -le 256 ]] || return 0
  mapfile -t marker < "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}"
  [[ ${#marker[@]} -eq 3 ]] || return 0
  printf '%s\n' "${marker[@]}" | cmp -s - "${SMITHYSTACHE_DOCKER_INPUT_HASH_FILE}" || return 0
  if [[ "${marker[0]}" != "$(smithystache_docker_image_input_hash)" ||
        ! "${marker[1]}" =~ ^sha256:[0-9a-f]{64}$ ||
        "${marker[2]}" != "$SMITHYSTACHE_DOCKER_PLATFORM" ]] ||
     ! smithystache_docker_image_identity_matches "${marker[1]}"; then
    return 0
  fi
  return 1
}

smithystache_ensure_docker_image() {
  if smithystache_needs_docker_build; then
    smithystache_docker_marker_path_safe || { echo "error: unsafe Docker input marker path" >&2; return 1; }
    local input_hash image_id
    input_hash="$(smithystache_docker_image_input_hash)"
    mkdir -p "${SMITHYSTACHE_DOCKER_CACHE_DIR}"
    echo "Building Docker test image (${SMITHYSTACHE_TEST_IMAGE})..."
    echo "The first build can take several minutes while Nix downloads dependencies; later builds reuse cached layers and are much faster."
    docker build --platform "${SMITHYSTACHE_DOCKER_PLATFORM}" -t "${SMITHYSTACHE_TEST_IMAGE}" -f Dockerfile .
    [[ "$input_hash" == "$(smithystache_docker_image_input_hash)" ]] || {
      echo "error: Docker inputs changed during build" >&2; return 1;
    }
    image_id="$(docker image inspect --format '{{.Id}}' "$SMITHYSTACHE_TEST_IMAGE")"
    if [[ ! "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]] || ! smithystache_docker_image_identity_matches "$image_id"; then
      echo "error: built Docker image identity/platform mismatch" >&2; return 1;
    fi
    smithystache_docker_write_input_marker "$input_hash" "$image_id"
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
