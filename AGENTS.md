# Smithplates

Smithy codegen plugin for SQL schema/migration and HTTP service/client output.
Bundled templates today: **Python** (SQL + FastAPI server + httpx client) and
**TypeScript** (HTTP client via axios or fetch).

## Conventions

Follow [`.cursor/rules/`](.cursor/rules/) — start with [`smithplates-build.mdc`](.cursor/rules/smithplates-build.mdc) and [`code-design.mdc`](.cursor/rules/code-design.mdc). Update user docs under [`docs/usage/`](docs/usage/) and contributor docs under [`docs/contributing/`](docs/contributing/) when behavior or integration steps change.

## Reference docs (fetch on demand)

* [`module-layout.md`](.ai-doc-reference/module-layout.md) — `modules/` responsibilities, precompilation summary, template roots; read when adding or moving code
* [`golden-template-tests.md`](.ai-doc-reference/golden-template-tests.md) — golden fixture layout, variant registration, refresh commands; read when changing codegen output or template tests
* [`plugin-consumer-config.md`](.ai-doc-reference/plugin-consumer-config.md) — `smithy-build.json` language-first config; read when wiring consumers or plugin settings
* [`codegen-output-deck.md`](.ai-doc-reference/codegen-output-deck.md) — `outputs.json` artifact-deck JSON contract (schema, bindings, path placeholders, verbatim-copy rule); read when adding/editing bundled artifacts or a new language's deck
* [`codegen-core.md`](.ai-doc-reference/codegen-core.md) — neutral IR, planner, strategies, and template `TemplateView` adapters; read when changing `smithplates-codegen-core` or renderer code
* [`cad-contract.md`](.ai-doc-reference/cad-contract.md) — Conceptual Architecture Document schema for architecture skills

## Human docs

* [`docs/usage/`](docs/usage/) — plugin configuration and generated-code usage
* [`docs/contributing/`](docs/contributing/) — validate commands, CI, architecture ([`CONTRIBUTING.md`](CONTRIBUTING.md) for `./validate` targets)
* [`CHANGELOG.md`](CHANGELOG.md) — release history and migration notes (keep current with user-visible changes)

## Decisions log

* `SqlTableTree` skips self-referential `@sqlForeignKey` edges when computing DDL render order so a table can reference itself inline in `CREATE TABLE`
* **Language-neutral codegen epic (`#34`, closed via `#35`–`#42`):** `smithplates-codegen-core` owns `NeutralType` / `Model` / `ModelSet` / `ServiceModel` / `OperationModel`, `TypeUsageAnalyzer`, declarative `NamingStrategy` → `Conventions`, `TypeRenderer`, and `CodegenPlanner` over `outputs.json` decks. Feature extractors (`HttpCoreModelExtractor`, `SqlCoreModelExtractor`) lower Smithy into parametric feature metadata; `SystemValidator` is the holistic post-extraction gate (model-set + service validators, duplicate ids, cyclic aliases, unresolved operation refs).
* **HTTP core extraction (`#36`):** `HttpCoreModelExtractor.extract` always runs `SystemValidator` (including `HttpCoreMetaValidator` for response status codes and `@websocket` shape rules). Operation-bound shapes get `HttpRequestMeta` / `HttpResponseMeta` from legacy HTTP binding IR via `HttpCoreMetaBuilder`; nested shapes get `HttpNestedField`.
* **SQL core extraction (`#36`):** `SqlCoreModelExtractor` lowers member types and `SqlTableMeta` (table name) for structural `SystemValidator` checks. DDL and derived-query rendering still read legacy `SqlSchema` / `SqlServiceIr` alongside the neutral model set.
* **SQL cutover (`#39` / `#42`):** SQL bundled output expansion uses `CodegenOutput` decks and `CodegenPlanner`; all db SSP templates render through `SqlNeutralServiceTemplateAttributes` (`TemplateView[ServiceModel[SqlServiceMeta, SqlOperationMeta], SqlMeta]` enriched at render time). Python-specific Scala helpers (`SqlCodegenPythonImports`, snake-case helpers, etc.) were removed; import formatting and casing live in SSP preambles. `SqlShapeIr` remains in context enrichment (for example `SqlCodegenUuidTypeNames`). **Exception:** Python SQL `enum` / `intEnum` artifacts are still rendered by a Scala side path (`string_enum` / `int_enum` in `SqlServiceCodegenRenderer.renderEnumArtifacts`), not by `outputs.json` model bindings.
* **HTTP cutover (`#40` / `#42`):** HTTP server/client/route-group expansion uses `CodegenOutput` decks and `CodegenPlanner`; all bundled HTTP SSP templates render through neutral `TemplateView` with `HttpNeutralModelTemplateAttributes`, `HttpNeutralServiceTemplateAttributes`, or `HttpNeutralRouteGroupTemplateAttributes`. `TemplateView` carries `typeRenderer: TypeRenderer` wired from `base_config.json` via `ConfigurableTypeRenderer`. The legacy HTTP bridge (`HttpCodegenTemplateView`, `HttpModelTypeNames`, package-name helpers) is removed.
* **Consumer-declarable outputs (`#41`, closed):** `additionalTemplatesDirectory` on `smithplates.<lang>.{sql,http.server,http.client}` loads a consumer `outputs.json` deck appended to bundled defaults; `overrides` replaces bundled outputs by id; `enableExternalTemplates` gates filesystem template directories. Consumer-deck static file copy (`CodegenStaticOutput`) and filesystem static copy are not wired yet. Duplicate resolved output paths are detected at codegen (plan/render) time.
* **Artifact decks are JSON, not Scala (HTTP and SQL service artifacts):** every bundled output deck is an `outputs.json` resource sitting beside that language's templates (`templates/python/src/http/{server,client,models}/outputs.json`, `templates/python/src/db/outputs.json`, `templates/typescript/src/http/**/outputs.json`), decoded by `planning/config/CodegenOutputDecoders` + `CodegenOutputDeck` in codegen-core. Deck composers load JSON rather than hardcoding per-language artifact tables; `CodegenOutputDeckLoader.load(templateDirectory, classLoader)` derives the resource path from the (language-encoding) template directory (`<dir>/outputs.json`), and the default template dir comes from `languageId`. A missing deck is a validation error ("missing codegen output deck: …"); a present deck whose referenced `.ssp` templates are absent is a separate "missing required templates" error. Copy-verbatim resources are identified language-neutrally by *not* ending in `.ssp`. A custom `templateDirectory` must ship its own `outputs.json` deck. **Caveat:** SQL string/int enum files remain outside the deck (see SQL cutover note above).
* **HTTP `@httpProblem` base model:** smithplates emits one shared `HttpProblem` model under `{rootNamespace}/smithplates/codegen/http/` (Python `http_problem.py`, TypeScript `httpProblem.ts`; Smithy namespace `smithplates.codegen.http`, aligned with the trait). `@httpProblem` error structures extend it; consumer structures named `Problem` are allowed when unrelated.
* **TypeScript HTTP clients:** bundled under `templates/typescript/` with `httpLibrary` `fetch` or `axios`. Client-only — no bundled TypeScript SQL or HTTP server. See `example/typescript/` and golden cases under `templates/typescript/tests/`.
* **WebSockets (`@websocket`):** bidirectional endpoints on `@httpService` operations (requires `@http` URI + `@tags`). Python FastAPI emits `websocket_routes.py`; Python and TypeScript clients emit websocket client modules. REST route/client generation skips `@websocket` operations.
* **`@sqlAutoIncrement`:** Integer table members map to SQLite `INTEGER PRIMARY KEY AUTOINCREMENT` / Postgres `GENERATED ALWAYS AS IDENTITY`; omitted from derived inserts; synthesized PRIMARY KEY clauses skip columns that already declare PK inline.
* **`@nestedProperties` body binding:** a single `@httpPayload` member with Smithy `@nestedProperties` becomes `HttpOperationBodyBinding.NestedDocument` — wire body is the payload target; the outer input shape is reconstructed for service dispatch.
* Implementation helpers live in public nested `object internal` companions, not `private` (see `code-design.mdc`). Some legacy `private` helpers remain in codegen-core and related modules; prefer `object internal` for new code.
* **Toolchain:** Scala/Java target **JDK 17** (`flake.nix` supplies `jdk17_headless`).