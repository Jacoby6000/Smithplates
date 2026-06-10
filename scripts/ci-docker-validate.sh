#!/usr/bin/env bash
# CI smoke checks for the Docker validate path:
#   1. flake dev shell inside the test image matches the host nix develop shell
#      (see scripts/lib/dev-shell-fingerprint.sh and CONTRIBUTING.md)
#   2. ./validate selects docker when nix is unavailable
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# shellcheck source=scripts/lib/docker-image.sh
source "${ROOT}/scripts/lib/docker-image.sh"
# shellcheck source=scripts/lib/validate-backend.sh
source "${ROOT}/scripts/lib/validate-backend.sh"

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

host_fp="${tmpdir}/host.txt"
container_fp="${tmpdir}/container.txt"

echo "==> fingerprint host nix develop shell"
nix develop "${ROOT}" --accept-flake-config -c "${ROOT}/scripts/lib/dev-shell-fingerprint.sh" \
  | LC_ALL=C sort > "${host_fp}"

smithystache_ensure_docker_image

echo "==> fingerprint container nix develop shell"
smithystache_docker_run -- ./scripts/lib/dev-shell-fingerprint.sh \
  | LC_ALL=C sort > "${container_fp}"

if ! diff -u "${host_fp}" "${container_fp}"; then
  echo "error: dev shell fingerprint mismatch between host nix and Docker nix" >&2
  exit 1
fi
echo "==> dev shell fingerprints match"

echo "==> validate backend auto-detection without nix"
docker_bin="$(command -v docker)"
test_bin="${tmpdir}/validate-detect-bin"
mkdir -p "${test_bin}"
ln -sf "${docker_bin}" "${test_bin}/docker"
detected="$(
  env -u SMITHYSTACHE_VALIDATE_BACKEND \
    PATH="${test_bin}" \
    "$(command -v bash)" --noprofile --norc -c "
      set -euo pipefail
      ROOT='${ROOT}'
      # shellcheck source=scripts/lib/validate-backend.sh
      source \"\${ROOT}/scripts/lib/validate-backend.sh\"
      smithystache_validate_detect_backend
    "
)"
if [[ "${detected}" != "docker" ]]; then
  echo "error: expected docker backend without nix, got: ${detected}" >&2
  exit 1
fi
echo "==> validate selects docker when nix is unavailable"
