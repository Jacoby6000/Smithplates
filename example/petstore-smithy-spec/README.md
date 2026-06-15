# Petstore Smithy model

Language-neutral Smithy sources for the petstore reference implementation. Directory
layout mirrors namespace layout: `petstore.api` under `petstore/api/`, `petstore.db`
under `petstore/db/`.

Reference implementations consume this tree from their own `smithy-build.json`
files. The Python project at [`../python/`](../python/) runs Smithy build with
`sources` pointing at `petstore/` and writes generated artifacts under
`../python/src/generated/`, `../python/tests/`, and `../python/db/migrations/`.

## Layout

```
petstore-smithy-spec/
  petstore/
    api/                     namespace petstore.api
      api-types.smithy       enums, errors, unions, shared HTTP value types
      api.smithy             HTTP operations and request/response shapes
      http-service.smithy    `@httpService` Petstore service
    db/                      namespace petstore.db
      db-types.smithy        SQL column/value types
      database.smithy        `@sqlTable` schema and `@sqlService` repositories
```
