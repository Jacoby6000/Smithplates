# SQL architecture

The SQL pipeline turns Smithy persistence models into schema DDL, repository protocols, dialect implementations, migration runners, and generated tests.

## Pipeline

```text
Smithy model
  -> SQL schema IR
  -> SQL service/query IR
  -> DDL renderers
  -> query renderers
  -> service codegen context
  -> Scalate SSP templates
  -> generated Python DB artifacts
```

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

## DDL renderers

DDL renderers live in:

- `smithplates-sql-ddl-renderer-common`
- `smithplates-sql-ddl-renderer-sqlite`
- `smithplates-sql-ddl-renderer-postgres`

They turn SQL schema IR into dialect DDL statements. The build plugin currently writes DDL-only migration files through `DialectRenderers.renderDdlOnly`.

Typical changes:

- Add dialect-specific column syntax.
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

- service codegen settings and artifact selection;
- codegen context construction;
- generated model/protocol/implementation/test views;
- Scalate SSP template rendering;
- bundled DB template precompilation.

Shared model/protocol rendering is dialect-free when no SQL dialect is enabled. Dialect-specific implementations, migration services, generated tests, SQL bindings, and schema DDL contexts are only built when a concrete dialect renderer is configured.

Typical changes:

- Add or remove generated DB artifacts.
- Change template view attributes.
- Change output file naming or source/test directory handling.
- Change generated migration service or integration-test context.

## Plugin orchestration

`smithplates-plugin` wires settings to the SQL pipeline:

- validates `smithplates.sql`;
- extracts schema and service IR;
- writes enabled dialect migration files;
- renders configured SQL language targets;
- writes generated artifacts through Smithy's file manifest.

## Change map

| Goal | Primary modules | Tests to start with |
|------|-----------------|---------------------|
| Add a SQL schema trait | `smithplates-sql-ir`, maybe DDL renderers | `smithplatesSqlIr/test`, affected renderer tests |
| Add a derived query shape | `smithplates-sql-service-ir`, query renderers, templates | service IR tests, query renderer tests, golden cases |
| Add a SQL dialect | DDL renderer, query renderer, artifact config, plugin wiring | new renderer tests, golden cases, dialect IT |
| Change generated Python DB output | `smithplates-sql-service-renderer`, `templates/python/src/db` | renderer unit tests, golden render tests, Python harness |
| Change plugin SQL config | `smithplates-plugin` settings and validators | plugin settings specs, build-plugin/golden tests |

Keep extraction contracts and rendering contracts separate. If a change can be expressed in IR first, do that before teaching dialect renderers or templates about it.
