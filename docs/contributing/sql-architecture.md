# SQL architecture

The SQL pipeline turns Smithy persistence models into schema DDL, repository protocols, dialect implementations, migration runners, and generated tests.

SQL service codegen uses **two layers**:

1. **Feature IR** — `SqlSchema` / `SqlServiceIr` for DDL, derived queries, and enrichment.
2. **Neutral model set + planner** — `SqlCoreModelExtractor` → `ModelSet` / `ServiceModel`, then `CodegenPlanner` over `templates/<language>/src/db/outputs.json`, rendering through `SqlNeutralServiceTemplateAttributes` on `TemplateView`.

## Pipeline

```text
Smithy model
  -> SQL schema IR (SqlSchema)
  -> SQL service/query IR (SqlServiceIr)
  -> SqlCoreModelExtractor (neutral ModelSet + SqlServiceMeta)
  -> SystemValidator
  -> DDL renderers (schema / migrations; feature IR only)
  -> query renderers (derived DML; feature IR)
  -> CodegenPlanner (outputs.json deck + enabled dialect variants)
  -> Scalate SSP via SqlNeutralServiceTemplateAttributes
  -> generated Python DB artifacts
```

SQL string/int enum files are still emitted by a **Scala side path** (`string_enum` / `int_enum` templates in `SqlServiceCodegenRenderer`), not by deck model bindings. HTTP enums are deck-driven. See [Custom templates — SQL enum side path](../usage/custom-templates.md#sql-enum-side-path).

## Schema IR

`smithplates-sql-ir` owns:

- `@sqlTable` extraction;
- table, column, relationship, index, and type ADTs;
- Smithy trait IDL and trait service registration for schema traits.

Start here when changing how Smithy structures become database schema.

Typical changes:

- Add or change a SQL modeling trait.
- Change type mapping from Smithy shapes to SQL column types.
- Change relationship extraction or table/member ordering.
- Add schema validation rules.

## Service and query IR

`smithplates-sql-service-ir` owns:

- `@sqlService` service extraction;
- derived query extraction for operations such as insert, update, delete, select, and select-one;
- binding derived queries to Smithy operation shape ids.

Start here when changing what SQL operations mean before dialect rendering.

Typical changes:

- Add a new derived operation trait.
- Change how `DerivedStruct` expands into operation input/output models.
- Change select/join/projection semantics.
- Change validation that binds operations to queries.

## Neutral extraction and planning

`smithplates-smithy-neutral` + SQL feature extractors own:

- lowering member types and `SqlTableMeta` for structural `SystemValidator` checks;
- building parametric `ServiceModel[SqlServiceMeta, SqlOperationMeta]` lists used by the planner.

`smithplates-codegen-core` owns:

- `CodegenPlanner`, `outputs.json` decoding, path templates, overrides, and collision checks;
- `TemplateView` + `TypeRenderer` / `Conventions` wiring for SSP attributes.

Consumer `additionalTemplatesDirectory` decks are appended here (see [Configuration](../usage/configuration.md#custom-codegen-outputs)). Consumer-deck static copy remains unwired.

## DDL renderers

DDL renderers live in:

- `smithplates-sql-ddl-renderer-common`
- `smithplates-sql-ddl-renderer-sqlite`
- `smithplates-sql-ddl-renderer-postgres`

They turn SQL schema IR into dialect DDL statements. The build plugin currently writes DDL-only migration files through `DialectRenderers.renderDdlOnly`. Foreign-key columns include reviewer-facing comments in `CREATE TABLE`. Postgres renders foreign-key constraints after all table/index statements as `ALTER TABLE ... ADD CONSTRAINT`; SQLite keeps constraints inline because it cannot add table constraints after creation.

Typical changes:

- Add dialect-specific column syntax (including `@sqlAutoIncrement`).
- Change index, enum, foreign-key, or default-expression DDL.
- Add a new schema-level DDL feature.

## Query renderers

Query renderers live in:

- `smithplates-sql-service-query-renderer`
- `smithplates-sql-service-query-renderer-common`
- `smithplates-sql-service-query-renderer-sqlite`
- `smithplates-sql-service-query-renderer-postgres`

They turn service/query IR into dialect SQL statements and bind metadata used by generated Python implementations.

Typical changes:

- Change placeholder style or bind ordering.
- Change INSERT/UPDATE/DELETE/SELECT rendering.
- Add result-field metadata needed by templates.

## Service renderer

`smithplates-sql-service-renderer` owns:

- service codegen settings and deck loading (`SqlServiceCodegenDbArtifacts`);
- planner expansion and Scalate SSP template rendering;
- `SqlNeutralServiceTemplateAttributes` enrichment from feature IR;
- SQL enum side-path rendering;
- bundled DB template precompilation.

Shared model/protocol rendering is dialect-free when no SQL dialect is enabled. Dialect-specific implementations, migration services, generated tests, SQL bindings, and schema DDL contexts are only built when a concrete dialect renderer is configured. Artifact lists come from `outputs.json` (`shared` + enabled `variants`), not from hardcoded Scala path tables.

Typical changes:

- Add or remove generated DB artifacts (edit `outputs.json`, then templates).
- Change template view attributes / SSP preambles.
- Change output path placeholders or source/test directory handling.
- Change generated migration service or integration-test context.

## Plugin orchestration

`smithplates-plugin` wires settings to the SQL pipeline:

- validates `smithplates.<language>.sql` (strict Circe decoding);
- extracts schema and service IR and runs neutral extraction + `SystemValidator`;
- writes enabled dialect migration files;
- renders configured SQL language targets through the planner;
- writes generated artifacts through Smithy's file manifest.

## Change map

| Goal | Primary modules | Tests to start with |
|------|-----------------|---------------------|
| Add a SQL schema trait | `smithplates-sql-ir`, maybe DDL renderers | `smithplatesSqlIr/test`, affected renderer tests |
| Add a derived query shape | `smithplates-sql-service-ir`, query renderers, templates | service IR tests, query renderer tests, golden cases |
| Add a SQL dialect | DDL renderer, query renderer, `outputs.json` variant, plugin wiring | new renderer tests, golden cases, dialect IT |
| Change generated Python DB output | `templates/python/src/db` (+ `outputs.json`), maybe `smithplates-sql-service-renderer` | renderer unit tests, golden render tests, Python harness |
| Change plugin SQL config | `smithplates-plugin` settings and validators | plugin settings specs, build-plugin/golden tests |
| Change planner / deck semantics | `smithplates-codegen-core`, deck JSON | `smithplatesCodegenCore/test`, golden cases |

Keep extraction contracts and rendering contracts separate. If a change can be expressed in IR first, do that before teaching dialect renderers or templates about it. Prefer editing `outputs.json` over adding Scala artifact tables when introducing new generated files.
