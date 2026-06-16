#!/usr/bin/env bash
# Commit example smithy-build.json updates produced by render-smithy-build.sh after CI tests pass.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

rendered=(
  example/python/smithy-build.json
  example/python/openapi/smithy-build.json
)

for path in "${rendered[@]}"; do
  if [[ -f "${path}" ]]; then
    git add "${path}"
  fi
done

if git diff --staged --quiet; then
  echo "No example smithy-build.json changes to commit."
  exit 0
fi

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

version="$(
  sbtn --no-colors 'print smithplatesPlugin/version' 2>/dev/null | tr -d '\r' | head -n 1
)"

git commit -m "$(cat <<EOF
Update example smithy-build configs (${version:-CI render}).

Rendered from templates by CI after example tests pass.
EOF
)"

if [[ -z "${CI_PUSH_REF:-}" ]]; then
  echo "error: CI_PUSH_REF is required to push committed smithy-build configs" >&2
  exit 1
fi

git push origin "HEAD:${CI_PUSH_REF}"
