# Smithplates

Smithy codegen plugin for SQL schema/migration and HTTP service/client output (currently Python templates).

## Conventions

Follow [`.cursor/rules/`](.cursor/rules/) — start with [`smithplates-build.mdc`](.cursor/rules/smithplates-build.mdc) and [`code-design.mdc`](.cursor/rules/code-design.mdc). Update user docs under [`docs/usage/`](docs/usage/) and contributor docs under [`docs/contributing/`](docs/contributing/) when behavior or integration steps change.

## Reference docs (fetch on demand)

* [`module-layout.md`](.ai-doc-reference/module-layout.md) — `modules/` responsibilities, precompilation summary, template roots; read when adding or moving code
* [`golden-template-tests.md`](.ai-doc-reference/golden-template-tests.md) — golden fixture layout, variant registration, refresh commands; read when changing codegen output or template tests
* [`plugin-consumer-config.md`](.ai-doc-reference/plugin-consumer-config.md) — `smithy-build.json` language-first config; read when wiring consumers or plugin settings
* [`cad-contract.md`](.ai-doc-reference/cad-contract.md) — Conceptual Architecture Document schema for architecture skills

## Human docs

* [`docs/usage/`](docs/usage/) — plugin configuration and generated-code usage
* [`docs/contributing/`](docs/contributing/) — validate commands, CI, architecture ([`CONTRIBUTING.md`](CONTRIBUTING.md) for `./validate` targets)

## Decisions log

* `SqlTableTree` skips self-referential `@sqlForeignKey` edges when computing DDL render order so a table can reference itself inline in `CREATE TABLE`
* `SystemValidator` (in codegen-core) is the holistic validation gate after extraction: model-set + service validators plus cross-entity duplicate ids, cyclic aliases, and unresolved operation refs. `#35` closed; work continues on `#36` (Smithy → core extraction) on branch `issue-36-smithy-extraction`.
* **HTTP core extraction (`#36`):** `HttpCoreModelExtractor.extract` always runs `SystemValidator` (including `HttpCoreMetaValidator` for response status codes). Operation-bound shapes get `HttpRequestMeta` / `HttpResponseMeta` from legacy HTTP binding IR via `HttpCoreMetaBuilder`; nested shapes get `HttpNestedField`.
* **SQL core extraction (`#36` / deferred `#39`):** `SqlCoreModelExtractor` lowers member types and `SqlTableMeta` (table name) only. Column types, `@sqlJson`, `@sqlVarchar`, and related per-member SQL facts stay in legacy `SqlSchema` until `#39` extends `SqlMeta` and wires feature validators. Use `SqlCoreModelExtractor.extractAndValidate` for structural `SystemValidator` checks today; full SQL meta parity is not in scope for `#36`.
* **SQL cutover (`#39` in progress):** SQL bundled output expansion now starts from `CodegenOutput` decks and `CodegenPlanner`; the renderer uses a bridge `TemplateRenderer` that builds the legacy `SqlCodegenServiceContext` per planned service/template so existing Python DB SSP output stays stable. Full removal of stringly SQL views/import helpers is still pending.
* Implementation helpers live in public nested `object internal` companions, not `private` (see `code-design.mdc`)
