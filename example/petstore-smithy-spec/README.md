# Petstore Smithy model

Language-neutral Smithy sources for the petstore reference implementation. Directory
layout mirrors namespace layout: `petstore.api` under `petstore/api/`, `petstore.db`
under `petstore/db/`.

Reference implementations consume this tree from their own `smithy-build.json`
files. The Python project at [`../python/`](../python/) renders
[`../python/smithy-build.json.template`](../python/smithy-build.json.template) with the
current `smithplatesPlugin/version`, then runs the Smithy CLI (`smithy build`) with
`sources` pointing at `petstore/` and writes generated artifacts under
`../python/src/generated/`, `../python/tests/`, and `../python/db/migrations/`.

## Layout

```
petstore-smithy-spec/
  petstore/
    api/                     namespace petstore.api
      api-types.smithy       enums, errors, unions, shared HTTP value types
      api.smithy             HTTP operations (REST + `@websocket`), request/response shapes
                             (`UpdatePet` uses `@nestedProperties` on `@httpPayload`)
      http-service.smithy    `@httpService` Petstore service
    db/                      namespace petstore.db
      db-types.smithy        SQL column/value types
      database.smithy        `@sqlTable` schema and `@sqlService` repositories
                             (`OrderLine.id` uses `@sqlAutoIncrement`)
```