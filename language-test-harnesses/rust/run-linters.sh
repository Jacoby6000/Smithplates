#!/usr/bin/env bash
set -euo pipefail
root="$(git rev-parse --show-toplevel)"
export CARGO_TARGET_DIR="${root}/target/language-test-harnesses/rust"
cd "${root}/language-test-harnesses/rust"
cargo fmt --check
cargo clippy --locked --all-targets -- -D warnings
