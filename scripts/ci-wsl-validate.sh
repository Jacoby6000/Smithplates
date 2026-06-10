#!/usr/bin/env bash
# Run ./validate from WSL on Windows CI using a single-user Nix install.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export NIX_CONFIG="${NIX_CONFIG:-experimental-features = nix-command flakes}"

if [[ -n "${SMITHYSTACHE_WSL_CI_CACHE:-}" ]]; then
  mkdir -p \
    "${ROOT}/.ci-cache/wsl-home" \
    "${ROOT}/.ci-cache/coursier" \
    "${ROOT}/.ci-cache/sbt" \
    "${ROOT}/.ci-cache/ivy2/cache" \
    "${ROOT}/.ci-cache/uv"
  export HOME="${ROOT}/.ci-cache/wsl-home"
  mkdir -p "${HOME}/.cache"
  ln -sfn "${ROOT}/.ci-cache/coursier" "${HOME}/.cache/coursier"
  ln -sfn "${ROOT}/.ci-cache/uv" "${HOME}/.cache/uv"
  ln -sfn "${ROOT}/.ci-cache/sbt" "${HOME}/.sbt"
  ln -sfn "${ROOT}/.ci-cache/ivy2" "${HOME}/.ivy2"
fi

if [[ ! -e "${HOME}/.nix-profile/etc/profile.d/nix.sh" ]]; then
  curl --retry 3 -fsSL https://nixos.org/nix/install | sh -s -- --no-daemon
fi

# shellcheck disable=SC1091
source "${HOME}/.nix-profile/etc/profile.d/nix.sh"

if ! command -v docker >/dev/null 2>&1 && [[ -z "${DOCKER_HOST:-}" ]]; then
  if [[ -S /var/run/docker.sock ]]; then
    :
  elif command -v docker.exe >/dev/null 2>&1; then
    export DOCKER_HOST="npipe:////./pipe/docker_engine"
  fi
fi

exec "${ROOT}/validate" "$@"
