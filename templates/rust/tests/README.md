# Rust golden fixtures

`http-json-client-api` covers a single catalog service: JSON serde models,
enums/unions/aliases and wire names; labels, repeated queries and request headers;
static/dynamic response headers; document/nested JSON payloads; empty success;
service errors and bearer/header/cookie auth alternatives.

Refresh with `sbtn 'generateGoldenTemplatesFor rust http-json-client-api'`.
The shared golden suite compares formatted generated output, and
`language-test-harnesses/rust` compiles it and runs loopback HTTP tests.
`./validate --target rust` also compiles fresh generated output with the same lockfile.
