#!/usr/bin/env bash
# Print a stable fingerprint of the flake dev shell for host/container parity checks.
#
# Used by scripts/ci-docker-validate.sh (CI validate-docker job) to assert that
# `nix develop` on the host matches `nix develop` inside smithystache-test:local.
#
# Maintenance: when you add build systems, language targets, or other tooling to
# devShells.default in flake.nix, extend this script so CI continues to compare
# the same surface area. Typical updates:
#   - add the command to the `for cmd in ...` presence loop
#   - add a *-version= line when a meaningful --version output exists
# Do not fingerprint the Nix CLI itself (host and image pin different releases).
#
# See CONTRIBUTING.md § "Docker dev-shell parity (CI)".
set -euo pipefail

for cmd in java sbtn sbt uv docker git pre-commit python3 nix; do
  if command -v "${cmd}" >/dev/null 2>&1; then
    printf '%s=present\n' "${cmd}"
  else
    printf '%s=missing\n' "${cmd}"
  fi
done

java -version 2>&1 | head -1 | sed 's/^/java-version=/'
uv --version 2>/dev/null | head -1 | sed 's/^/uv-version=/'
docker --version 2>/dev/null | head -1 | sed 's/^/docker-version=/'
python3 --version 2>/dev/null | head -1 | sed 's/^/python-version=/'
