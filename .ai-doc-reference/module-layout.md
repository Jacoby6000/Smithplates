# Module Layout

Read this when adding or moving code under `modules/`, wiring new renderers, or
tracing dependencies between IR, renderers, and the build plugin.

User-facing plugin docs: [`docs/usage/`](../docs/usage/). Contributor
architecture (including template precompilation deep dive):
[`docs/contributing/architecture.md`](../docs/contributing/architecture.md).

## Modules under `modules/`

* `smithplates-codegen-core` — language-neutral codegen ADTs under `codegen.core`: `ModelId`/`ModelKind`, the `NeutralType` type system (optionality via `OptionalT`, `NeutralType.optional` normalizes nested optionals), `Model[A]`/`ModelSet[A]`/`ServiceModel[A, B]`/`OperationModel[A]`, per-position metadata wrappers (`ModelMeta`/`ServiceMeta`/`OperationMeta`), `TypeResolver[A]` (alias-chasing `underlying`) + `TypeUsageAnalyzer` (deterministic first-occurrence `usedTypes`), `CodegenPlanner` / `CodegenOutput` decks / `TemplateView`, and layered validation culminating in `SystemValidator` (`CodegenValidated` / `CodegenValidationError`). No target-language syntax and no dependency on SQL/HTTP/renderer modules. Shipped by the closed `#34` epic; consumed by `smithplates-smithy-neutral`, feature IR adapters, and both renderers at plan time. See [`codegen-core.md`](codegen-core.md).
* `smithplates-smithy-neutral` — shared Smithy → `NeutralType` lowering (`SmithyNeutralTypeResolver`, `ModelIds`, `SmithyPrelude`) and `SmithyModelExtractor` (post-extraction `SystemValidator` hook). Depends on `smithplates-codegen-core` + Smithy model; used by feature IR adapters
* `smithplates-sql-ir` — schema ADTs under `sql.model`, table extraction (`SqlIrExtractor`); `SqlTableTree` / `SqlTableMemberOrdering` / `SqlText` live in `sql`. `SqlTableTree` skips self-referential `@sqlForeignKey` edges when computing render order so a table can reference itself inline in `CREATE TABLE`.
* `smithplates-sql-ddl-renderer-common` — shared DDL rendering (`SqlSchemaDdlRenderer`, `SqlShared`) under `sql.ddl.renderer.common`
* `smithplates-sql-service-ir` — query/service IR (`SqlServiceIr`), extractors, and neutral extraction (`SqlCoreModelExtractor` / `SqlMeta` under `sql.service.core` → `ModelSet[SqlMeta]` + `ServiceModel` via `smithplates-smithy-neutral`; legacy string IR still used for query rendering and the SSP fragment bridge)
* `smithplates-sql-service-query-renderer` — `SqlQueryRenderer` trait, `SqlParameterizedStatement`, dialect-neutral query output types
* `smithplates-sql-service-query-renderer-common` — shared dialect-neutral query rendering (`SqlQueryRendering`) under `sql.service.query.renderer.common`
* `smithplates-sql-service-query-renderer-postgres` / `smithplates-sql-service-query-renderer-sqlite` — dialect `SqlQueryRenderer` implementations
* `smithplates-sql-ddl-renderer-sqlite` / `smithplates-sql-ddl-renderer-postgres` — dialect schema DDL only (`SqlSchemaDdlRenderer`); depend on `smithplates-sql-ir` and `smithplates-sql-ddl-renderer-common`
* `smithplates-sql-service-renderer` — Scalate SSP rendering and language-neutral template attributes in `codegen` (type/bind metadata only; rendering lives in templates); bundled Python SSP sources live under [`templates/python/src/db/`](../templates/python/src/db/) and are packaged as compile resources; compile depends on query-renderer base only. Its `packageBin` jar also bundles **precompiled** SSP template classes (see precompilation below)
* `smithplates-http-ir` — HTTP trait IDL/classes (`@httpService`, `@httpProblem`, `@httpStaticHeader`, `@websocket`), `@httpService` extraction, HTTP service IR, projection transforms, body-binding resolution (`NestedDocument` for `@nestedProperties`), and `HttpCoreModelExtractor` → `ModelSet[HttpMeta]` + `ServiceModel` via `smithplates-smithy-neutral`
* `smithplates-http-service-renderer` — Scalate SSP rendering for HTTP service and client codegen via neutral `TemplateView` attributes; bundled templates under [`templates/python/src/http/`](../templates/python/src/http/) (FastAPI server, httpx client, models) and [`templates/typescript/src/http/`](../templates/typescript/src/http/) (axios/fetch client + models), packaged as compile resources with **precompiled** SSP template classes
* `smithplates-scalate-precompiler` — shared helper (`ScalateTemplatePrecompiler`) that derives the per-template-root Scala `packagePrefix` and ahead-of-time compiles bundled SSP templates to JVM classes; depended on by both renderer modules at compile (runtime `packagePrefix`) and by their build-time precompile tasks
* `smithplates-plugin` — thin Smithy build plugin; wires DDL and query renderers; the only artifact consumers reference by coordinate (`com.jacoby6000:smithplates-plugin`). Its **entire transitive compile graph is published** (`publishedModuleSettings`) so Maven resolves the dependency jars — including renderer jars carrying precompiled templates; only `smithplates-testkit` and the dialect IT modules stay unpublished (`unpublishedModuleSettings`)
* `smithplates-testkit`, `smithplates-sql-ddl-renderer-postgres-it`, `smithplates-sql-ddl-renderer-sqlite-it` — testkit + dialect integration tests

## Template precompilation (summary)

Bundled Scalate SSP templates are compiled to JVM classes at build time and
packaged into the renderer jars (`scalateTemplatePrecompileSettings` in
`build.sbt` → cached `precompiledTemplateClasses` task → forked
`*TemplatePrecompilerMain` → `Compile / packageBin / mappings`). Runtime engines
and the precompiler share the same per-template-root `packagePrefix`
(`ScalateTemplatePrecompiler.packagePrefix`, e.g.
`scalate.precompiled.python.src.db`) so `TemplateEngine.load` (with
`allowReload=false`) loads precompiled classes instead of recompiling. Templates
are precompiled for both URI conventions (root-relative top-level and
root-prefixed `include`/`render`); injected `preamble.ssp` fragments are
excluded. The default compile/test flow is unchanged — only the packaged jar
gains the extra classes.

Full design: [`docs/contributing/architecture.md#template-precompilation`](../docs/contributing/architecture.md#template-precompilation).

## Testkit & IT modules

* `smithplates-testkit` holds shared Smithy fixtures and JDBC DDL helpers (`src/main`)
* Dialect renderer IT modules (`smithplates-sql-ddl-renderer-postgres-it`, `smithplates-sql-ddl-renderer-sqlite-it`) contain **only** `src/test`

## Codegen templates

`@sqlService` Python DB codegen and `@httpService` codegen (Python FastAPI/httpx
+ TypeScript axios/fetch clients, including WebSockets) use Scalate SSP templates
under [`templates/<language>/src/<feature>/`](../templates/) with an `outputs.json`
deck beside each template root. Bundled Python templates organize reusable
snippets under `fragments/` and include them via
`<% include("fragments/...") %>` / `<% render("fragments/...", Map(...)) %>`;
other languages may use a different layout. Plugin config validation checks
top-level templates required by enabled dialects/frameworks.
