#!/usr/bin/env bash
# Import the release signing key for sbt-ci-release / sbt-pgp on CI runners.
#
# Requires PGP_SECRET (base64-encoded armored private key) and optionally
# PGP_PASSPHRASE. GitHub-hosted Ubuntu images ship GnuPG 2.x; importing via
# `gpg --batch --import` avoids silent import failures seen with older paths.
set -euo pipefail

if [[ -z "${PGP_SECRET:-}" ]]; then
  echo "error: PGP_SECRET is not set (required for signed publish)" >&2
  echo "Configure repository secrets PGP_SECRET and PGP_PASSPHRASE; see docs/contributing/getting-started.md." >&2
  exit 1
fi

mkdir -p "${HOME}/.gnupg"
chmod 700 "${HOME}/.gnupg"

cat > "${HOME}/.gnupg/gpg.conf" <<'EOF'
use-agent
pinentry-mode loopback
EOF

cat > "${HOME}/.gnupg/gpg-agent.conf" <<'EOF'
allow-loopback-pinentry
EOF

chmod 600 "${HOME}/.gnupg/"* 2>/dev/null || true
gpg-connect-agent reloadagent /bye >/dev/null 2>&1 || true

echo "==> Importing PGP signing key"
echo "${PGP_SECRET}" | base64 --decode | gpg --batch --yes --import

if ! gpg --list-secret-keys --keyid-format=long >/dev/null 2>&1; then
  echo "error: no secret keys available after import; check PGP_SECRET encoding (use base64 -w0)" >&2
  gpg --list-secret-keys --keyid-format=long || true
  exit 1
fi

echo "==> Available signing keys:"
gpg --list-secret-keys --keyid-format=long
