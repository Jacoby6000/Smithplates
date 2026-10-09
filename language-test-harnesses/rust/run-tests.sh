#!/usr/bin/env bash
set -euo pipefail
root="$(git rev-parse --show-toplevel)"
export CARGO_TARGET_DIR="${root}/target/language-test-harnesses/rust"
cd "${root}/language-test-harnesses/rust"
cargo test --locked
fresh="${root}/templates/rust/tests/http-json-client-api/out/source/smithplates/src/generated/example/mod.rs"
if [[ ! -f "${fresh}" ]]; then
  echo "error: fresh generated Rust module missing; run ./validate --target rust" >&2
  exit 1
fi
mkdir -p "${CARGO_TARGET_DIR}/fresh/src"
cp Cargo.toml Cargo.lock rust-toolchain.toml "${CARGO_TARGET_DIR}/fresh/"
printf '#[path = "%s"]\npub mod generated;\n' "${fresh}" > "${CARGO_TARGET_DIR}/fresh/src/lib.rs"
cargo check --locked --manifest-path "${CARGO_TARGET_DIR}/fresh/Cargo.toml"
