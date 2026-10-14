# Testing

Smithplates uses several layers of tests because the project validates both Scala extraction/rendering logic and generated target-language output.

## Primary entry point

Use `./validate` from the repository root:

```bash
./validate
./validate build
./validate test --target plugin
./validate lint,test --target python/db/sqlite
```

`./validate` uses Nix when available and falls back to Docker when needed.

The Docker CI smoke job also runs `bash scripts/tests/docker-image-cache.sh`.
This cheap regression suite exercises the production build/export/load helpers
with fake Docker commands; it does not require a Docker daemon or Nix build.
The versioned image cache stores a gzip archive and fixed data-only metadata
binding its producer input hash, image ID, platform and archive checksum.
The hash covers the Dockerfile, `.dockerignore`, copied dev-shell inputs
(`flake.nix`, `flake.lock`, `build.sbt`), platform and cache helper scripts.
Only verified metadata can restore the local producer marker. Legacy, missing,
stale, malformed or corrupt caches rebuild; they never stamp current inputs onto
an unverified image. An exact cache hit skips only the image build, not dev-shell
parity/backend checks or the ordinary validation tests. Dependency/tooling cache
restoration is not a cached validation verdict, and image reuse does not prove
registry availability for integration-test images.

### Nix tooling cache

CI installs Nix 2.24.9 with the existing installer/daemon settings. It restores
only three user-owned staging files (`closure.nar.gz`, `paths.txt`, `manifest.json`)
using pinned `actions/cache/restore`, then registers paths with ordinary
`nix-store --import`. Tar never restores `/nix`, its database, or profiles.

**Trust:** the installer already makes the runner a trusted daemon client.
Legacy Nix exports omit official signatures; normal import uses that existing
authority. Archive hashes, NAR parsing, and path verification establish integrity,
not producer authentication. Only main push jobs save this format. GitHub's cache
ACLs restrict producers and allow PRs to read main caches; other trusted main
workflows remain inside this trust boundary. No signing policy or daemon trust
setting is changed. Never put credentials or private source in this public cache.

The exact-only `smithplates-nix-nar-v1` key includes repository, OS/architecture,
Nix version, and sorted named hashes of the lock, flake, `build.sbt`, setup action,
transport helper, and its tests. There are no prefix/legacy fallbacks. An invalid
immutable exact entry is not overwritten or deleted. Version mismatch, backend
failure, corrupt/oversized data, partial imports, and failed readiness probes fall
back to the mandatory current-flake realization. Already registered prefixes from
a failed import are retained, not repaired or deleted.

Only the realized dev-shell environment and its selected interactive Bash runtime
closure are eligible. The helper rejects the repository's self store path,
`*-source` paths and derivations. It does not export build targets, test results,
profiles, configuration, secrets, or mutable dependency caches. Staging/profile
cleanup runs before the Docker job's existing export/GC lifecycle.

Initial limits are 4,096 paths, 6 GiB summed NAR bytes, 1 GiB per path, 2 GiB
compressed, 1 MiB metadata, ten minutes per transport operation, and 3 GiB importer
address space. Streaming and free-space checks reserve room for both the archive
and Actions' temporary tar, plus 2 GiB spare
(at least 6 GiB + 6 MiB free before export).
These are guardrails, not measurements; CI logs selected counts/sizes and free bytes.
Oversize export is an optimization failure, never permission to raise quotas or GC.

`python3 scripts/test-ci-nix-cache.py` exercises the production helper with stubs;
it proves neither daemon trust nor warm reuse. Setup separately builds a random
tiny unsigned fixture, exports it, deletes **only that fixture**, imports it through
the installed daemon without overrides, verifies it, and removes it. This tests
new registration rather than import of an already-present path.

`warm-store-ready` requires exact backend restoration, successful import and path
verification, and a current-flake `nix develop --max-jobs 0 --option substitute
false` probe. It does not promise network-free evaluation. Normal realization
and every validation target still run; a dev shell is not a cached Nix check.
The first approved main run must log its cold export and explicit backend saved
key. A green save step alone is insufficient. A later natural eligible run must
demonstrate exact restoration and no-build readiness before claiming cross-run
warm reuse. PRs cannot populate this production cache.

## Choosing what to run

| Change type | Minimum focused checks | Broader checks before PR |
|-------------|------------------------|--------------------------|
| SQL trait or schema extraction | `sbtn smithplatesSqlIr/test` | `./validate test --target plugin` |
| SQL service/query extraction | `sbtn smithplatesSqlServiceIr/test` | `./validate test --target plugin` |
| DDL renderer behavior | affected renderer unit tests | affected dialect IT module with Docker |
| Query renderer behavior | affected query-renderer module tests | SQL golden tests and Python harness for affected dialect |
| SQL service renderer Scala logic | focused `smithplatesSqlServiceRenderer/testOnly ...` | `./validate lint,test --target python/db/<dialect>` |
| Python DB SSP templates | golden render test for affected case | Python harness linters and pytest |
| HTTP IR or transforms | `sbtn smithplatesHttpIr/test` | HTTP golden tests and example HTTP tests |
| HTTP SSP templates (Python) | HTTP golden case under `templates/python/tests/` | `./validate --target examples/python` |
| HTTP SSP templates (TypeScript) | Golden **render** under `templates/typescript/tests/` via `CodegenTemplateTestSuite` | `./validate --target examples/typescript` (example **typecheck** + cross-impl HTTP tests — not golden render) |
| Codegen core / planner | `sbtn smithplatesCodegenCore/test` | `./validate test --target plugin` |
| Plugin settings or orchestration | focused plugin spec | `./validate test --target plugin` plus affected generated-output tests |
| Docs only | `git diff --check`, reusable component check if relevant | pre-commit docs hook |

When in doubt, choose the smallest test that exercises the changed contract first, then run the harness that validates generated output.

## Scala tests

Run focused module tests with `sbtn`:

```bash
sbtn smithplatesSqlIr/test
sbtn smithplatesSqlServiceIr/test
sbtn smithplatesSqlServiceRenderer/test
sbtn smithplatesPlugin/test
```

Use focused `testOnly` runs while iterating:

```bash
sbtn 'smithplatesPlugin/testOnly *SmithplatesSqlSettingsSpec'
sbtn 'smithplatesSqlServiceRenderer/testOnly *SqlServiceCodegenRendererSpec'
```

## Dialect integration tests

Dialect DDL integration tests apply generated schema DDL to real databases through testcontainers:

```bash
sbtn smithplatesSqlDdlRendererPostgresIt/test
sbtn smithplatesSqlDdlRendererSqliteIt/test
```

Docker must be installed and running.

## Golden render tests

Golden template tests render Smithy fixtures under `templates/<language>/tests/` and compare the generated tree to `expected/`. Python and TypeScript cases share [`CodegenTemplateTestSuite`](../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/CodegenTemplateTestSuite.scala).

```bash
./scripts/run-template-golden-tests.sh
sbtn 'smithplatesPlugin/testOnly *CodegenTemplateTestSuite*'
```

`./scripts/run-template-golden-tests.sh` without a Python-scoped `--target` runs the **full** suite (Python and TypeScript). The script's `--target` flag is Python harness-oriented; for TypeScript-only golden iteration use the `sbtn …CodegenTemplateTestSuite*` form above.

Refresh expected output only when generated output intentionally changes:

```bash
sbtn 'generateGoldenTemplatesFor python <case-name> [<case-name> ...]'
sbtn 'generateGoldenTemplatesFor typescript <case-name> [<case-name> ...]'
```

See [`templates/python/tests/README.md`](../../templates/python/tests/README.md) and [`templates/typescript/tests/README.md`](../../templates/typescript/tests/README.md).

## Generated Python execution tests

The Python language harness runs ruff, mypy, and pytest against golden `expected/` trees:

```bash
./language-test-harnesses/python/run-linters.sh
./language-test-harnesses/python/run-tests.sh
```

Postgres variants require Docker. There is no TypeScript golden harness yet; use the example typecheck below.

## HTTP shared example tests

Shared HTTP scenario tests live under `example/tests/`. They run the reference server and generated client together:

```bash
cd example/tests
./run-tests.sh python python
./run-tests.sh typescript python
./run-tests.sh openapi-reference-python python
./run-tests.sh python python health-check
```

Use these when HTTP route behavior, generated app wiring, OpenAPI export/client coordination, or example adapters change.

## Example tests

Petstore references have separate validation:

```bash
./validate --target examples/python
./validate --target examples/typescript
```

Use these when changing example wiring, generated HTTP integration, OpenAPI export, or generated-client coordination.

## Troubleshooting `sbtn`

Always use `sbtn` from the repository root. If the thin client hangs or stops responding, clear stale processes and retry:

```bash
ps aux | rg -i 'sbtn|sbt-launch'
pkill -f 'sbtn-x86_64-pc-linux' || true
pkill -f 'sbt-launch\.jar' || true
```

Prefer one focused `sbtn` command at a time over long chained reload/test sequences.
