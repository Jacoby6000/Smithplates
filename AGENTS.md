# Smithplates

Smithy codegen plugin for SQL schema/migration and HTTP service/client output (currently Python templates).

## Conventions

Follow [`.cursor/rules/`](.cursor/rules/) — start with [`smithplates-build.mdc`](.cursor/rules/smithplates-build.mdc) and [`code-design.mdc`](.cursor/rules/code-design.mdc). Update user docs under [`docs/usage/`](docs/usage/) and contributor docs under [`docs/contributing/`](docs/contributing/) when behavior or integration steps change.

## Reference docs (fetch on demand)

* [`module-layout.md`](.ai-doc-reference/module-layout.md) — `modules/` responsibilities, precompilation summary, template roots; read when adding or moving code
* [`golden-template-tests.md`](.ai-doc-reference/golden-template-tests.md) — golden fixture layout, variant registration, refresh commands; read when changing codegen output or template tests
* [`plugin-consumer-config.md`](.ai-doc-reference/plugin-consumer-config.md) — `smithy-build.json` language-first config; read when wiring consumers or plugin settings
* [`codegen-output-deck.md`](.ai-doc-reference/codegen-output-deck.md) — `outputs.json` artifact-deck JSON contract (schema, bindings, path placeholders, verbatim-copy rule); read when adding/editing bundled artifacts or a new language's deck
* [`codegen-core.md`](.ai-doc-reference/codegen-core.md) — neutral IR, planner, strategies, and current legacy-bridge state; read when changing `smithplates-codegen-core` or renderer cutover code
* [`cad-contract.md`](.ai-doc-reference/cad-contract.md) — Conceptual Architecture Document schema for architecture skills

## Human docs

* [`docs/usage/`](docs/usage/) — plugin configuration and generated-code usage
* [`docs/contributing/`](docs/contributing/) — validate commands, CI, architecture ([`CONTRIBUTING.md`](CONTRIBUTING.md) for `./validate` targets)

## Decisions log

* `SqlTableTree` skips self-referential `@sqlForeignKey` edges when computing DDL render order so a table can reference itself inline in `CREATE TABLE`
* `SystemValidator` (in codegen-core) is the holistic validation gate after extraction: model-set + service validators plus cross-entity duplicate ids, cyclic aliases, and unresolved operation refs. `#35` closed; work continues on `#36` (Smithy → core extraction) on branch `issue-36-smithy-extraction`.
* **HTTP core extraction (`#36`):** `HttpCoreModelExtractor.extract` always runs `SystemValidator` (including `HttpCoreMetaValidator` for response status codes). Operation-bound shapes get `HttpRequestMeta` / `HttpResponseMeta` from legacy HTTP binding IR via `HttpCoreMetaBuilder`; nested shapes get `HttpNestedField`.
* **SQL core extraction (`#36` / deferred `#39`):** `SqlCoreModelExtractor` lowers member types and `SqlTableMeta` (table name) only. Column types, `@sqlJson`, `@sqlVarchar`, and related per-member SQL facts stay in legacy `SqlSchema` until `#39` extends `SqlMeta` and wires feature validators. Use `SqlCoreModelExtractor.extractAndValidate` for structural `SystemValidator` checks today; full SQL meta parity is not in scope for `#36`.
* **SQL cutover (`#39`):** SQL bundled output expansion uses `CodegenOutput` decks and `CodegenPlanner`; the renderer bridges planned service templates through `SqlCodegenServiceContext` / `ServiceTemplateView` until db SSP templates migrate to neutral `TemplateView`. Legacy string IR (`SqlShapeIr`, `SqlIrTypeNameResolver`, import helpers) remains for extraction, query rendering, and the bridge.
* **HTTP cutover (`#40` / `#42`):** HTTP server/client/route-group expansion uses `CodegenOutput` decks and `CodegenPlanner`; all bundled HTTP SSP templates render through neutral `TemplateView` with `HttpNeutralModelTemplateAttributes`, `HttpNeutralServiceTemplateAttributes`, or `HttpNeutralRouteGroupTemplateAttributes`. The legacy `HttpCodegenTemplateView` bridge is removed.
* **Consumer-declarable outputs (`#41`, closed):** `additionalTemplatesDirectory` on `smithplates.<lang>.{sql,http.server,http.client}` loads a consumer `outputs.json` deck appended to bundled defaults; `overrides` replaces bundled outputs by id; `enableExternalTemplates` gates filesystem template directories. Consumer-deck static file copy (`CodegenStaticOutput`) and filesystem static copy are not wired yet. Duplicate resolved output paths are detected at codegen (plan/render) time.
* **Artifact decks are JSON, not Scala (HTTP and SQL):** every bundled output deck is an `outputs.json` resource sitting beside that language's templates (`templates/python/src/http/{server,client,models}/outputs.json`, `templates/python/src/db/outputs.json`), decoded by `planning/config/CodegenOutputDecoders` + `CodegenOutputDeck` in codegen-core. There are **no hardcoded language paths in Scala**: `CodegenOutputDeckLoader.load(templateDirectory, classLoader)` derives the resource path from the (language-encoding) template directory (`<dir>/outputs.json`), and the default template dir comes from `languageId`. `HttpServiceCodegenApiArtifacts`/`HttpClientCodegenApiArtifacts`/`SqlServiceCodegenDbArtifacts` only compose loaded decks (shared + enabled `variants`) and take the template dir as a parameter. A missing deck is a validation error ("missing codegen output deck: …") surfaced by the language-target validators; a present deck whose referenced `.ssp` templates are absent is a separate "missing required templates" error. Copy-verbatim resources are identified language-neutrally by *not* ending in `.ssp` (Scalate's template extension) — see `SqlServiceCodegenDbArtifacts.isRenderedTemplate`; the former `bundledTemplatePaths` set is gone. A custom `templateDirectory` must ship its own `outputs.json` deck.
* **HTTP `@httpProblem` base model:** smithplates emits one shared `HttpProblem` Pydantic model at `{rootNamespace}/smithplates/codegen/http/http_problem.py` (Smithy namespace `smithplates.codegen.http`, aligned with the trait). `@httpProblem` error structures extend it; consumer structures named `Problem` are allowed when unrelated.
* **Codegen cleanup (`#42` in progress):** Remove dead code from the pre-cutover pipeline where templates no longer reference it. HTTP legacy bridge removed; SQL db templates still use `ServiceTemplateView` / `SqlCodegenServiceContext`. Track remaining bridge surface in [`codegen-core.md`](.ai-doc-reference/codegen-core.md).
* Implementation helpers live in public nested `object internal` companions, not `private` (see `code-design.mdc`)
