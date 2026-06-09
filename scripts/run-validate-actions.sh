#!/usr/bin/env bash
# Run one or more validate actions (lint, test) in the current environment.
# Used by ./validate when entering nix develop or Docker once for all actions.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

# shellcheck source=scripts/lib/validate-actions.sh
source "${ROOT}/scripts/lib/validate-actions.sh"

smithystache_validate_run_actions "$@"
