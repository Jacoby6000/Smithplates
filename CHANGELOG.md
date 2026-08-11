# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.8.0] - 2026-08-11

### Added

- Consumer SSP templates can inspect domain-neutral effective Smithy traits on
  represented services, operations, models, structure fields, union variants,
  and enum values through `AppliedTrait` and `SmithyNodeValue`.

## [0.7.1] - 2026-08-04

### Fixed

- Python HTTPX/HTTPX2 clients now import URL encoding helpers when authentication
  requires a query-parameter collection but the operation has no modeled query
  bindings, fixing bearer-, header-, and cookie-authenticated requests at runtime.

## [0.7.0] - 2026-08-04

### Added

- Smithy 2.0 `@auth` and `@optionalAuth` lowering with ordered bearer,
  header/query API-key, and Smithplates `@httpCookieAuth` alternatives.
- Generated FastAPI credential extraction, application-supplied identity
  verification, and typed auth context injection for protected and
  optional-auth handlers.
- Generated authentication providers for Python HTTPX/HTTPX2 sync and async
  REST clients and TypeScript fetch clients. Browser cookie auth uses
  `credentials: "include"` without exposing `HttpOnly` cookie values.

### Security

- Authentication extraction and generation fail closed for unsupported or
  unconfigured schemes, invalid API-key settings, conflicting HTTP bindings,
  authenticated WebSockets, and authenticated axios clients.

## [0.6.0] - 2026-08-03

### Added

- Python REST clients can target HTTPX2 with `httpLibrary: "httpx2"` in
  `async`, `sync`, or `both` mode. HTTPX remains the default for compatibility.
- Codegen output decks can declare `defaultVariant`, preserving implicit
  framework or client-library selection when a bundled deck offers multiple
  variants.

### Changed

- `CodegenOutputDeck` now exposes the optional `defaultVariant` field. This is
  a public case-class shape change for codegen-extension authors upgrading to
  the next minor release.

## [0.5.1] - 2026-08-03

### Added

- Python/httpx REST clients can now be generated in `async`, `sync`, or `both`
  modes. Async remains the default, while synchronous clients use
  `httpx.Client` and the same generated operation bindings and response parser.

### Fixed

- Python/httpx and TypeScript fetch/axios operation response bindings now merge
  service-level errors with operation-local errors and deduplicate repeated
  shapes, preserving modeled status, media type, and response headers.
- TypeScript WebSocket connections now expose native browser `error` events via
  a typed `onError` listener without imposing reconnect behavior.
- Python/httpx and TypeScript fetch/axios clients now serialize `@httpQuery`
  members, preserve wire names, omit absent optional values, and encode values
  using RFC 3986 percent encoding.
- REST and WebSocket clients now percent-encode URI labels, including each
  segment of greedy labels, and TypeScript WebSocket connect methods accept
  modeled path-label arguments without retaining path-only inputs as messages.
- TypeScript optional Smithy members are emitted as optional properties, fetch
  clients set JSON content types unless explicitly modeled, and WebSocket
  imports are deduplicated when operations share message types.

## [0.5.0] - 2026-07-30

### Added

- `{{serviceModuleName}}` placeholder now appears in all service-scoped output
  paths. A single output entry with no `services` filter produces a per-service
  subdirectory (e.g. `generated/example/http/stream_host/app_factory.py`)
  naturally — eliminating the need for per-service `packageName` overrides in
  `smithy-build.json` or post-generation directory merging.
- `servicePackageName(ctx)` and `serviceModuleDir(ctx)` template helpers for
  service-scoped package imports and module paths.
- `packageSeparator` added to the `Conventions` trait (delegates to
  `strategy.packageSeparator`).

### Changed

- All Python and TypeScript `outputs.json` files now include
  `{{serviceModuleName}}/` in `binding: { type: "service" }` and
  `binding: { type: "operation" }` output paths.
- All SSP templates use `servicePackageName` / `serviceModuleDir` instead of
  `packageName` / `namespaceModuleDir` for service-scoped artifacts. Model
  artifacts (shared across services) still use `packageName` / `namespaceModuleDir`.

## [0.4.2] - 2026-07-30

### Fixed

- TypeScript client import specifiers now use `ctx.conventions.memberName(tag)`
  to match the camelCase output path binding (`{{tagName}}Client.ts`). Previously
  the SSP helper `tsClientModuleName` returned `tag + "Client"` (raw snake_case
  from the Smithy tag, e.g. `v1_analyticsClient`), which did not match the
  generated filename (`v1AnalyticsClient.ts`). Consumers no longer need a
  post-generation snake_case→camelCase import-specifier fixup script. The
  `Client` suffix is now inline in the TypeScript templates, and the
  language-neutral `clientModuleName` helper is unchanged.

## [0.4.1] - 2026-07-30

### Fixed

- `route_group_protocol.ssp` now emits `...` for the Protocol body when a
  service tag has only `@websocket` operations (no REST handlers). Previously
  the generated Python `Protocol` class had an empty body, which is a syntax
  error in Python 3.

## [0.4.0] - 2026-07-26

### Added

- Per-service output scoping: the `outputs` array on `http` and `sql` targets
  specifies `sourceOutputDir`, `testOutputDir`, optional `services` filter,
  and optional `packageName` per codegen pass. Multiple entries produce
  independent output trees for multi-service models (e.g. separate API/runner
  packages from one Smithy model). The `services` filter accepts both full
  shape IDs (`com.example#MyService`) and bare shape names (`MyService`);
  an empty list is treated as omitted. A warning is logged when a `services`
  filter matches no service in the model. Shared `once`-bound artifacts
  (e.g. `conftest.py`) written identically by multiple entries are
  deduplicated; differing content at the same path fails with a collision
  error. See issue #57.
- `@sqlAutoIncrement` on integer primary-key members: SQLite
  `INTEGER PRIMARY KEY AUTOINCREMENT`, Postgres `GENERATED ALWAYS AS IDENTITY`.
  Auto-increment columns are omitted from derived inserts.
- HTTP `@nestedProperties` on a single `@httpPayload` member flattens the
  payload target as the wire body and reconstructs the outer operation input
  for service dispatch (Python FastAPI, Python/httpx, TypeScript axios/fetch).

### Changed

- **Breaking:** `sourceOutputDir` and `testOutputDir` are no longer set at the
  language level. They are now required fields in each `http.outputs[]` /
  `sql.outputs[]` entry. The `outputs` array itself is required — at least one
  entry must be specified. Existing configs must migrate by moving
  `sourceOutputDir`/`testOutputDir` into an `outputs` array inside each
  `http` and/or `sql` target block.
- Documentation refresh for post-`v0.3.0` behavior, namespace-aware SQL artifact
  paths, consumer decks, TypeScript clients, and WebSockets.
- Consumer usage docs: guide chooser, day-1 checklist, version discovery,
  wiring snippets, trait cheat sheets, common pitfalls, and a custom-templates
  append tutorial.
- Petstore reference now demonstrates `@sqlAutoIncrement` (`OrderLine.id`) and
  `@nestedProperties` on `UpdatePetInput.body`.
- Safer SQLite boolean column coercion with dialect-specific `_read_bool_col`
  helpers and a `sql-boolean-column` golden case.

## [0.3.0] - 2026-07-17

Language-neutral codegen cutover (`#34` / `#35`–`#42`), TypeScript HTTP clients,
WebSockets, and related generated-output fixes.

### Added

- **Language-neutral codegen core** (`#35`, PR `#43`): `NeutralType`, unified
  `Model` / `ModelSet`, services/operations, `TypeUsageAnalyzer`, and
  `SystemValidator`.
- **Smithy → neutral extraction** (`#36`, PR `#44`): `HttpCoreModelExtractor`
  and `SqlCoreModelExtractor` with holistic post-extraction validation.
- **Declarative naming / type strategies** (`#37`, PR `#45`):
  `base_config.json`-driven `NamingStrategy` → `Conventions` and
  `TypeRenderer`; plugin JSON decoding is strict Circe (unknown keys and
  unsupported values fail clearly).
- **`CodegenPlanner` and output bindings** (`#38`, PR `#46`): path templates,
  tag grouping, override-by-id, and duplicate-path detection.
- **SQL planner cutover** (`#39` / `#42`, PRs `#47`, `#50`): bundled SQL
  artifacts declared in `templates/python/src/db/outputs.json`; templates render
  through `SqlNeutralServiceTemplateAttributes` on neutral `TemplateView`.
- **HTTP planner cutover** (`#40` / `#42`, PRs `#48`, `#50`): server, client,
  and model decks under `templates/*/src/http/**/outputs.json`; shared RFC 9457
  `HttpProblem` under `smithplates.codegen.http`.
- **Consumer-declarable outputs** (`#41`, PR `#49`):
  `additionalTemplatesDirectory` appends a consumer `outputs.json` deck;
  `overrides` replaces bundled outputs by id; `enableExternalTemplates` gates
  filesystem SSP directories (emits a security warning).
- **TypeScript HTTP clients** (PR `#52`): bundled axios/fetch clients and models;
  petstore example under `example/typescript/`; cross-implementation tests
  (TypeScript client → Python server).
- **WebSockets** (PR `#53`): `@websocket` on `@httpService` operations generates
  FastAPI WebSocket routes, Python WebSocket clients, and TypeScript native
  WebSocket clients. REST generation skips `@websocket` operations.
- JDK **17** toolchain target (Scala and Java).

### Changed

- **Breaking — Python SQL models:** table/query structures and union variants
  use dataclasses with explicit JSON mapping instead of TypedDict-style shapes
  (`72c054c1`). HTTP models remain Pydantic.
- **Breaking — generated paths:** SQL and HTTP artifacts are namespace-aware
  (for example `{{smithyNamespaceDir}}/models/{{serviceModuleName}}_models.py`)
  rather than the older `db/model/...` layout. See
  [`docs/usage/configuration.md`](docs/usage/configuration.md#namespace-aware-layout)
  and [`templates/python/src/db/outputs.json`](templates/python/src/db/outputs.json).
- **Breaking — shared `HttpProblem`:** one framework-owned base model is emitted
  under `{rootNamespace}/smithplates/codegen/http/`. Consumer structures named
  `Problem` are allowed when unrelated to `@httpProblem`.
- **Breaking — plugin config:** unknown keys and incorrect types in
  `smithy-build.json` plugin settings are rejected. Key spelling/casing must
  match the documented schema.
- SQL/HTTP bundled artifact lists moved from Scala hardcoding into colocated
  `outputs.json` decks. Custom `templateDirectory` roots must ship their own
  deck.
- Python-specific Scala naming/import helpers removed; casing and imports live
  in SSP preambles (`#42`).
- Bug fixes for response-variant model emission/imports, transitive nested
  `@sqlJson` mapping, safer SQLite boolean conversion, generated SQL lifecycle
  tests, and generated test `conftest.py` (PR `#51`).

### Migration notes (from 0.2.x)

1. Pin / bump the `com.jacoby6000:smithplates-plugin` coordinate to `0.3.0`
   (or a matching snapshot) and re-run `smithy build`.
2. Expect regenerated Python SQL models to use dataclasses; update hand-written
   adapters that constructed TypedDict-like dicts.
3. Update import paths and sync scripts for namespace-prefixed output
   (`<sourceOutputDir>/<smithy namespace path>/...`).
4. Import `HttpProblem` from the generated `smithplates.codegen.http` package
   rather than a per-service problem base, if you previously depended on older
   problem-detail layout.
5. To extend bundled artifacts, prefer `additionalTemplatesDirectory` +
   `outputs.json` over forking the entire template tree. Full
   `templateDirectory` replacement still works and requires a complete deck.
6. Build with **JDK 17**.

### Known limitations retained in 0.3.x

- Consumer-deck `CodegenStaticOutput` / filesystem static copy from
  `additionalTemplatesDirectory` is not wired yet (`#41` residual).
- SQL string/int enum artifacts are still rendered via a Scala side path
  (`string_enum` / `int_enum` templates), not declared in `outputs.json`.
- Feature-specific SQL/HTTP IR remains for DDL, derived queries, and binding
  enrichment alongside the neutral model set.

## [0.2.5] - 2026-06-19

Previous stable release before the language-neutral codegen epic.
See git history `v0.2.5` for the full 0.2.x line.

[Unreleased]: https://github.com/Jacoby6000/Smithplates/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/Jacoby6000/Smithplates/compare/v0.7.1...v0.8.0
[0.7.1]: https://github.com/Jacoby6000/Smithplates/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/Jacoby6000/Smithplates/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/Jacoby6000/Smithplates/compare/v0.5.1...v0.6.0
[0.5.1]: https://github.com/Jacoby6000/Smithplates/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/Jacoby6000/Smithplates/compare/v0.4.2...v0.5.0
[0.4.2]: https://github.com/Jacoby6000/Smithplates/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/Jacoby6000/Smithplates/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/Jacoby6000/Smithplates/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Jacoby6000/Smithplates/compare/v0.2.5...v0.3.0
[0.2.5]: https://github.com/Jacoby6000/Smithplates/releases/tag/v0.2.5
