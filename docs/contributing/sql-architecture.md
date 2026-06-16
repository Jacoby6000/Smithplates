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

## Service and query IR

`smithplates-sql-service-ir` owns:

- `@sqlService` service extraction;
- derived query extraction for operations such as insert, update, delete, select, and select-one;
- binding derived queries to Smithy operation shape ids.

Start here when changing what SQL operations mean before dialect rendering.

## DDL renderers

DDL renderers live in:

- `smithplates-sql-ddl-renderer-common`
- `smithplates-sql-ddl-renderer-sqlite`
- `smithplates-sql-ddl-renderer-postgres`

They turn SQL schema IR into dialect DDL statements. The build plugin currently writes DDL-only migration files through `DialectRenderers.renderDdlOnly`.

## Query renderers

Query renderers live in:

- `smithplates-sql-service-query-renderer`
- `smithplates-sql-service-query-renderer-common`
- `smithplates-sql-service-query-renderer-sqlite`
- `smithplates-sql-service-query-renderer-postgres`

They turn service/query IR into dialect SQL statements and bind metadata used by generated Python implementations.

## Service renderer

`smithplates-sql-service-renderer` owns:

- service codegen settings and artifact selection;
- codegen context construction;
- generated model/protocol/implementation/test views;
- Scalate SSP template rendering;
- bundled DB template precompilation.

Shared model/protocol rendering is dialect-free when no SQL dialect is enabled. Dialect-specific implementations, migration services, generated tests, SQL bindings, and schema DDL contexts are only built when a concrete dialect renderer is configured.

## Plugin orchestration

`smithplates-plugin` wires settings to the SQL pipeline:

- validates `smithplates.sql`;
- extracts schema and service IR;
- writes enabled dialect migration files;
- renders configured SQL language targets;
- writes generated artifacts through Smithy's file manifest.
