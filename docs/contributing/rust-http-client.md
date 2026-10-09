# Rust client templates and tests

Rust resources live under `templates/rust/`, are copied to the HTTP renderer
classpath, and SSP models/client templates are ahead-of-time compiled and
packaged in its jar. Naming/type syntax lives in `base_config.json`; artifacts
are declared in the ordinary JSON decks. Grouped model subjects use the shared
planner dispatch, not a parallel Rust generator.

`RustHttpCapabilities` beside the renderer checks selected-service cardinality,
flat name collisions, supported neutral types and original reachable Smithy
traits. Original recursion is checked before legacy HTTP extraction so recursive
collection aliases cannot overflow its resolver. Rust syntax stays in SSP and
verbatim runtime resources, not Scala string renderers.

Run the standard entry point:

```bash
./validate
./validate --target rust
```

The Rust target runs the shared golden suite, then Cargo tests against committed
goldens and `cargo check --locked` plus the same wire tests against the freshly
generated module. Its
lockfile pins the complete dependency graph, and `rust-toolchain.toml` pins Rust
1.93 with rustfmt/clippy. The dev shell supplies rustup (which installs that
toolchain on first use); no Nix lockfile-wide Rust package upgrade is required.
Cargo uses `target/language-test-harnesses/rust`, outside golden fixtures.

The test-only Smithy build runner applies rustfmt to generated Rust, just as it
applies ruff to Python. Consumers do not need rustfmt to generate code. The
language harness checks rustfmt and clippy with warnings denied and uses a
Tokio loopback HTTP server rather than external services. CI discovers the Rust
harness, and pre-commit checks Rust fixtures/harness changes.

Refresh only the intended case:

```bash
sbtn 'generateGoldenTemplatesFor rust http-json-client-api'
```

Keep protocol additions fail-closed until both generator negative tests and
executable wire tests cover them. See [usage/limitations](../usage/rust-http-client.md)
for the actual supported subset. Do not claim other targets' full parity.

`TemplateView.resolutionModels` provides the complete planner resolution set for
response models and recursive alias lookup; `usedTypes` remains the direct import
set. The wire fixture covers service-error headers, named scalar response headers,
optional nested bodies, and operation names that collide with runtime error names.

Operation metadata retains `requestStaticHeaders` independently of a reused
structure's request/response classification. The loopback fixture exercises the
same structure as both input and output. Payload-wrapper rejection inspects
effective response members, not variant IDs; validated plugin tests assert no
artifact writes for success and error wrappers with headers.

The wire harness checks static response header cardinality for custom success
headers and modeled problem content types: one matching value succeeds, while
missing, mismatched, repeated (including identical), and comma-folded values
fail with redacted `InvalidBinding` diagnostics.
