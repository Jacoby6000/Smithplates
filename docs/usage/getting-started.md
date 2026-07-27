# Getting started

This guide is for a consumer project that wants to run the `smithplates` Smithy build plugin and inspect generated output.

## Find a plugin version

| Situation | How to pick `<version>` |
|-----------|-------------------------|
| **Published release** | Use the latest stable tag on [Maven Central — `smithplates-plugin`](https://central.sonatype.com/artifact/com.jacoby6000/smithplates-plugin) (for example `0.3.0`). Release notes: [`CHANGELOG.md`](../../CHANGELOG.md). |
| **Main-branch snapshot** | After a push to `main`, CI publishes a unique `-SNAPSHOT` (see [Release](../contributing/release.md)). Add the Central Portal snapshots resolver and pin the exact version string from the publish log. |
| **Local development of this repo** | From the Smithplates root: `sbtn publishM2`, then `sbtn print smithplatesPlugin/version`. Put that version in the consumer `smithy-build.json`. |

Consumers reference only `com.jacoby6000:smithplates-plugin`; its published transitive dependencies carry the trait IDL, renderers, and precompiled bundled templates.

## 1. Add the plugin dependency

Add the published plugin coordinate to the consumer project's `smithy-build.json`:

```json
{
  "version": "1.0",
  "maven": {
    "dependencies": [
      "com.jacoby6000:smithplates-plugin:<version>"
    ]
  }
}
```

## 2. Choose a path

Configure Smithplates under `plugins.smithplates.<language>`. At least one language must contain `sql` or `http`.

- Use [SQL quickstart](#sql-quickstart) when you want database schema, repositories, migration services, and generated DB tests.
- Use [HTTP quickstart](#http-quickstart) when you want FastAPI route wiring, service protocols, response helpers, and API models (add a `typescript` language entry for a fetch/axios client).
- Use both sections when the same consumer project generates SQL and HTTP artifacts. Keep the Smithy namespaces separate and bridge them in hand-written code.

## SQL quickstart

### Configure SQL generation

Enable one or more dialects for migration DDL and set output directories for generated repository artifacts:

```json
{
  "version": "1.0",
  "sources": ["model"],
  "maven": {
    "dependencies": [
      "com.jacoby6000:smithplates-plugin:<version>"
    ]
  },
  "plugins": {
    "smithplates": {
      "python": {
        "sql": {
          "sqlite": {
            "enable": true,
            "migrationLocation": "db/migrations/sqlite"
          },
          "outputs": [
            { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
          ]
        }
      }
    }
  }
}
```

### Author a SQL model

SQL models use persistence traits such as `@sqlTable`, `@sqlService`, and `@sqlDeriveInsert`.

```smithy
$version: "2.0"

namespace example.db

use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable

@sqlTable(name: "widgets")
structure Widget {
    @sqlPrimaryKey
    id: String

    name: String
}

@sqlDeriveInsert(targetTable: "example.db#Widget")
operation CreateWidget {
    input: DerivedStruct
    output: CreateWidgetOutput
}

structure CreateWidgetOutput {
    @required
    id: String
}

@sqlDeriveSelectOne(targetTable: "example.db#Widget")
operation GetWidget {
    input: DerivedStruct
    output: Widget
}

@sqlService
service WidgetRepository {
    version: "1"
    operations: [CreateWidget, GetWidget]
}
```

### SQL output

With the SQL configuration above, this model generates:

- a `v1_initial_schema.sql` migration file under `db/migrations/sqlite`;
- shared Python model and repository protocol files under `src/generated/example/db/`;
- a SQLite implementation, migration service, transaction helper, and generated pytest file under `src/generated/example/db/sqlite/` and `tests/example/db/sqlite/`.

### Minimal SQL wiring (Python)

After syncing generated files into your tree (and putting the package root on `PYTHONPATH` — see [Day-1 checklist](#day-1-checklist)):

```python
import aiosqlite
from generated.example.db.sqlite.sqlite_migrations import apply_migrations
from generated.example.db.sqlite.widget_repository_aiosqlite import WidgetRepository

async with aiosqlite.connect(":memory:") as connection:
    await apply_migrations(connection, "db/migrations/sqlite")
    repo = WidgetRepository(connection)
    widget_id = await repo.create_widget(name="demo")
    row = await repo.get_widget(id=widget_id)
```

Implement only what you need beyond derived methods: the generated `*_protocol.py` is the stable boundary for hand-written alternatives.

## HTTP quickstart

### Configure HTTP generation

Configure HTTP generation under the language entry. Omit `packageName` unless you need an exact import override — packages default from `rootNamespace` plus the Smithy namespace (see [Configuration](configuration.md)):

```json
{
  "version": "1.0",
  "sources": ["model"],
  "maven": {
    "dependencies": [
      "com.jacoby6000:smithplates-plugin:<version>"
    ]
  },
  "plugins": {
    "smithplates": {
      "python": {
        "http": {
          "server": {
            "webFramework": "fastapi"
          },
          "client": {
            "httpLibrary": "httpx"
          },
          "outputs": [
            { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
          ]
        }
      }
    }
  }
}
```

### Author an HTTP model

HTTP models use `@httpService`, standard Smithy `@http`, `@tags`, and optional Smithplates HTTP traits such as `@httpProblem`.

```smithy
$version: "2.0"

namespace example.api

use smithplates.codegen.http#httpService
use smithy.api#http
use smithy.api#readonly
use smithy.api#tags

@httpService
service WidgetApi {
    version: "1"
    operations: [HealthCheck]
}

@readonly
@tags(["system"])
@http(method: "GET", uri: "/health", code: 200)
operation HealthCheck {
    output: HealthCheckOutput
}

structure HealthCheckOutput {
    status: String
}
```

### HTTP output

With the HTTP configuration above, this model generates FastAPI app wiring, route modules, service protocol base classes, response helpers, and shared Pydantic models under `src/generated/example/api/` (for a service in namespace `example.api`). Enabling `http.client` also emits an httpx client registry and route-group clients under `…/client/` and `…/clients/`.

### Minimal HTTP wiring (Python)

```python
from generated.example.api.app_factory import create_app
from generated.example.api.apis.system_api_base import SystemApiServiceProtocol
from generated.example.api.health_check_output import HealthCheckOutput

class SystemApi(SystemApiServiceProtocol):
    async def health_check(self) -> HealthCheckOutput:
        return HealthCheckOutput(status="ok")

app = create_app(system_api=SystemApi())
```

Exact factory / registry names follow your tags and deck; the [Python petstore](../../example/python/src/server/) shows a multi-route-group layout including WebSockets.

### TypeScript client quickstart

Add a sibling language entry for a bundled fetch or axios client (no server templates for TypeScript today):

```json
"typescript": {
  "http": {
    "rootNamespace": "generated",
    "client": {
      "httpLibrary": "fetch"
    },
    "outputs": [
      { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
    ]
  }
}
```

See [HTTP plugin](http-plugin.md) and [`example/typescript/`](../../example/typescript/) for the full client layout.

## Using SQL and HTTP together

When one project uses both SQL and HTTP codegen, keep the Smithy namespaces separate:

- SQL persistence namespace, for example `example.db`.
- HTTP wire-contract namespace, for example `example.api`.

Do not reuse SQL table shapes as HTTP request or response shapes. Generated repositories and generated HTTP routes should meet in hand-written application code that maps API models to DB models.

See [Configuration](configuration.md) for the full settings model and [Examples](examples.md) for the Python and TypeScript petstore references.

## Run `smithy build`

Run `smithy build` from the consumer project. Smithy writes plugin artifacts under:

```text
build/smithy/source/smithplates/
```

Copy, sync, or otherwise project those files into your application layout. The [Python petstore example](../../example/python/) demonstrates this with [`scripts/run-example-build.sh`](../../scripts/run-example-build.sh).

For a first inspection, run:

```bash
smithy build
tree build/smithy/source/smithplates
```

Then decide whether your project commits generated files, syncs them as part of a build step, or treats them as build artifacts. The petstore example commits generated reference output so changes are reviewable.

## Day-1 checklist

After the first successful `smithy build`:

1. **Sync generated output** from `build/smithy/source/smithplates/<outputs.sourceOutputDir>` (and `<outputs.testOutputDir>`) into the layout your app imports — or point tooling at the Smithy output tree directly ([Configuration — Generated output ownership](configuration.md#generated-output-ownership)).
2. **Python imports:** put the directory that contains the `generated` package on `PYTHONPATH` (petstore uses `src/` so `generated` resolves from `src/generated/`).
3. **Runtime deps:** dialect drivers (`aiosqlite` / `psycopg`), FastAPI + Uvicorn for the server, `httpx` for the Python client, and the `websockets` package when you use a generated Python WebSocket client.
4. **Typecheck:** for Postgres generated tests, add `<outputs.testOutputDir>/<smithy namespace path>/postgres/stubs` to `mypy_path` (bundled testcontainers stubs).
5. **Migrations:** run the generated migration service against your DB before calling repositories; only the initial `v1_*.sql` file is produced today ([Limitations](limitations.md#migrations)).
6. **Do not edit** files under the generated tree — implement protocols and adapters in hand-written modules instead.

## Common pitfalls

| Pitfall | What to do |
|---------|------------|
| Sharing one Smithy structure between HTTP and SQL | Keep `example.api` and `example.db` (or equivalent) namespaces separate; map in app code. |
| Editing generated sources | Treat them as build output; extend via protocols, adapters, or [custom templates](custom-templates.md). |
| Expecting incremental migration SQL from model diffs | Only initial schema DDL is generated today — append later `vN_*.sql` files yourself. |
| Unknown keys in `smithy-build.json` plugin config | Decoding is strict; fix spelling/casing to match [Configuration](configuration.md). |
| Service filter matches nothing | If `outputs[].services` lists a shape name or ID not in the model, a warning is logged with the available services. Check spelling and namespace. |
| Path collision across `outputs` entries | Shared `once` artifacts with identical content are deduplicated silently; differing content at the same path fails. Use distinct `sourceOutputDir`/`testOutputDir` per entry or non-overlapping `services` filters. |
| OpenAPI export of `@websocket` operations | `stripSmithplatesHttpCodegenTraits` does **not** remove `@websocket`; filter those ops or keep them out of the OpenAPI projection. |
| Forgetting to enable a dialect | Shared SQL models/protocols can emit without dialects; driver implementations and migration DDL need `sqlite.enable` / `postgres.enable`. |
| Custom `templateDirectory` without `outputs.json` | Every template root must ship a deck — see [Custom templates](custom-templates.md). |

## Next reading

- [Configuration](configuration.md) for all settings and output locations.
- [SQL plugin](sql-plugin.md) for SQL traits, generated repositories, migrations, and transactions.
- [HTTP plugin](http-plugin.md) for FastAPI/WebSocket output, Python and TypeScript clients, route groups, and problem details.
- [Examples](examples.md) for the Python and TypeScript reference projects.
- [Custom templates](custom-templates.md) to append or replace bundled artifacts.
