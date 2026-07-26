# Configuration

Smithplates settings live under the `smithplates` plugin key in `smithy-build.json`.

```json
{
  "plugins": {
    "smithplates": {
      "<language>": {
        "sql": {
          "outputs": [
            { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
          ]
        },
        "http": {
          "outputs": [
            { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
          ]
        }
      }
    }
  }
}
```

`sourceOutputDir` and `testOutputDir` are configured **per output entry** inside the `outputs` arrays of each target (`http` and `sql`), not at the language level. At least one language entry must contain `sql` or `http`, and each `sql`/`http` target requires an `outputs` array with at least one entry. Settings validation accumulates errors and reports them at the Smithy build plugin boundary.

## Maven dependency

Consumers should add only the plugin coordinate:

```json
{
  "maven": {
    "dependencies": [
      "com.jacoby6000:smithplates-plugin:<version>"
    ]
  }
}
```

The plugin POM resolves the published transitive modules that contain SQL traits, HTTP traits, renderers, and precompiled bundled templates.

## Common layouts

### SQL only

Use this when a project only wants database schema, repository protocols, dialect implementations, migration services, and generated DB tests:

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

### HTTP only

Use this when a project only wants generated FastAPI wiring and/or HTTP clients from `@httpService` models:

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
      },
      "typescript": {
        "http": {
          "client": {
            "httpLibrary": "fetch"
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

### SQL and HTTP together

Use the same language entry with both `sql` and `http`. Keep SQL and HTTP Smithy namespaces separate, then bridge them in hand-written application code. A combined copy/paste example:

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
          "rootNamespace": "generated",
          "outputs": [
            { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
          ]
        },
        "http": {
          "rootNamespace": "generated",
          "server": {
            "webFramework": "fastapi",
            "packageName": "generated.api"
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

## Language-level fields

The language entry (`smithplates.<language>`) only carries `enableExternalTemplates` plus the `sql` and `http` target configs. Output directories are **not** set here — they live inside each target's `outputs` array (see [Output entry fields](#output-entry-fields)).

| Field | Required | Meaning |
|-------|----------|---------|
| `enableExternalTemplates` | No; default `false` | When `true`, allows `additionalTemplatesDirectory` to reference SSP templates outside the bundled classpath (see [Custom outputs](#custom-codegen-outputs)). Emits a build warning because external SSP executes arbitrary Scala at build time. |

Do not set `sourceOutputDir` or `testOutputDir` at the language level, under a dialect, or under `http.server`/`http.client`; configure them inside each target's `outputs` array.

## Output entry fields

Each `sql` and `http` target requires an `outputs` array with at least one entry. Every entry generates an independent output tree rooted at its own `sourceOutputDir`/`testOutputDir`.

```json
"http": {
  "outputs": [
    {
      "sourceOutputDir": "src/generated",
      "testOutputDir": "tests",
      "services": ["com.example.inventory#InventoryService"],
      "packageName": "generated.api"
    }
  ]
}
```

| Field | Required | Meaning |
|-------|----------|---------|
| `sourceOutputDir` | Yes | Base output directory for generated source artifacts in this output tree. |
| `testOutputDir` | Yes | Base output directory for generated test artifacts in this output tree. |
| `services` | No; omit to generate all services | Filters this output tree to the listed service shape IDs. Accepts either the full shape ID (`com.example#MyService`) or just the shape name (`MyService`). An empty list is treated as omitted (generate all services). |
| `packageName` | No | Override the derived import package for this output tree exactly (overrides `http.server.packageName` / `http.client.packageName` / `sql.packageName`). When omitted, packages include the Smithy service namespace. |

When a model has multiple services and you want independent output trees (separate source roots, package names, or service filters), add multiple `outputs` entries each with its own `services` filter. A single entry without `services` generates all services into one tree.

`migrationLocation` stays under the enabled dialect config inside `sql` (see [`smithplates.<language>.sql`](#smithplateslanguagesql)) — it is a runtime concept (where migration SQL files are written during build), not a codegen output path, so it is not part of `outputs`.

## `smithplates.<language>.sql`

SQL configuration has two independent concerns:

- Dialect keys (`sqlite`, `postgres`) control build-time migration DDL and dialect-specific generated implementations.
- `templateDirectory`, `rootNamespace`, and `packageName` control template selection and Python import packages for `@sqlService` artifacts in that language.
- `outputs` (required) declares the per-tree codegen output roots and optional per-tree service filters — see [Output entry fields](#output-entry-fields).

```json
{
  "python": {
    "sql": {
      "sqlite": {
        "enable": true,
        "migrationLocation": "db/migrations/sqlite"
      },
      "postgres": {
        "enable": true,
        "migrationLocation": "db/migrations/postgres"
      },
      "rootNamespace": "generated",
      "outputs": [
        { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
      ]
    }
  }
}
```

| Field | Required | Meaning |
|-------|----------|---------|
| `sqlite.enable` / `postgres.enable` | No; default `false` | Enables that dialect's DDL migration files and dialect-specific generated implementation artifacts. |
| `sqlite.migrationLocation` / `postgres.migrationLocation` | Yes when `enable` is `true` | Directory for versioned migration SQL files, such as `db/migrations/sqlite`. The value must be a directory path, not a `.sql` file. |
| `rootNamespace` | No; default `generated` for bundled Python | Prefix for Python import packages (for example `generated.example` for a service in namespace `example`). |
| `packageName` | No | Override the derived SQL import package exactly (for example `generated.db`). When omitted, packages include the Smithy service namespace. |
| `templateDirectory` | Required for non-bundled languages | Classpath template root. Bundled Python uses the packaged templates by default. A custom root must also contain an [`outputs.json` output deck](custom-templates.md#output-deck-outputsjson) beside the templates. |
| `additionalTemplatesDirectory` | No | Classpath (or external, with `enableExternalTemplates`) root containing an `outputs.json` deck appended to the bundled SQL deck. See [Custom outputs](configuration.md#custom-codegen-outputs). |
| `outputs` | Yes | Array of output tree entries (`sourceOutputDir`, `testOutputDir`, optional `services`, optional `packageName`). See [Output entry fields](#output-entry-fields). |

When a language `sql` block is configured with no enabled dialects, Smithplates renders only shared model and protocol artifacts. That shared output is dialect-free: it does not require a query renderer, DDL renderer, migration location, or dialect-specific template. `outputs` is still required.

## `smithplates.<language>.http`

HTTP configuration lives beside SQL under the language entry and contains `server` and/or `client` objects plus the required `outputs` array:

```json
{
  "python": {
    "http": {
      "server": {
        "webFramework": "fastapi",
        "packageName": "generated.api"
      },
      "client": {
        "httpLibrary": "httpx",
        "packageName": "generated.api_client"
      },
      "outputs": [
        { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
      ]
    }
  }
}
```

| Field | Required | Meaning |
|-------|----------|---------|
| `webFramework` | No; default `fastapi` | Web framework for generated server artifacts. Python/FastAPI is the bundled server today. |
| `httpLibrary` | No; default `httpx` for Python | HTTP client library. Bundled values: `httpx` (Python), `fetch` or `axios` (TypeScript). |
| `packageName` | No | Default derived import package for server or client output. Overridden per output tree by `outputs[].packageName`. When both are omitted, packages include the Smithy service namespace. |
| `rootNamespace` | No; default `generated` for bundled Python | Prefix for HTTP model and service import packages. |
| `modelsPackageName` | No | Override the derived models import package exactly. When omitted, per-shape model packages include each shape's Smithy namespace. |
| `templateDirectory` | Required for non-bundled languages | Classpath template root for custom HTTP templates. A custom root must also contain an [`outputs.json` output deck](custom-templates.md#output-deck-outputsjson) beside the templates. |
| `additionalTemplatesDirectory` | No | Root containing an `outputs.json` deck appended to the bundled server/client deck. See [Custom outputs](configuration.md#custom-codegen-outputs). |

## Custom codegen outputs

Consumers extend bundled SQL/HTTP codegen in two ways:

### `additionalTemplatesDirectory` (append to bundled outputs)

For **existing bundled features** (Python SQL/HTTP, TypeScript HTTP client), point
`additionalTemplatesDirectory` at a folder that ships an `outputs.json` deck
beside its templates — the same layout as bundled language template roots under
`templates/<language>/src/...`.

```json
{
  "python": {
    "sql": {
      "sqlite": { "enable": true, "migrationLocation": "db/migrations/sqlite" },
      "additionalTemplatesDirectory": "classpath:my-templates/sql",
      "outputs": [
        { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
      ]
    }
  }
}
```

The additional deck is **appended** to the bundled deck. Use `"overrides": "<bundled-id>"`
in the additional `outputs.json` to replace a bundled output (for example
`"python.http.models.structure"`). Additional output ids must be distinct from bundled ids.

Set `"enableExternalTemplates": true` on `smithplates.<language>` when templates in the
additional directory are not on the plugin classpath (filesystem paths). External SSP
templates execute arbitrary Scala at build time and bypass precompiled template classes.

#### Merge semantics and path collisions

The additional deck is **appended** to the bundled deck. The planner then resolves
`overrides` by id (replacing the bundled entry) and rejects **duplicate resolved output
paths** among the merged outputs.

Path collision detection runs at **codegen (plan/render) time**, not during
`smithy-build.json` validation. Resolved paths depend on Smithy model facts (service
namespaces, operation tags, enabled dialects, and path-template placeholders such as
`{{smithyNamespaceDir}}`), so config validation cannot prove collisions with complete
certainty before the model is loaded.

#### Static resource outputs

`outputs.json` may declare static (non-`.ssp`) entries for verbatim file copy.
Consumer-deck static outputs (`CodegenStaticOutput`) are **not wired yet** — only SSP
template bindings are rendered from `additionalTemplatesDirectory` today. Use
`templateDirectory` full-deck replacement when a custom language needs static support
files in the deck.

### `templateDirectory` (full deck replacement)

For **new languages** or **fully custom feature templates**, set `templateDirectory`
to a root that ships its own complete `outputs.json` deck. This replaces the bundled
deck for that block (existing behavior). See [custom-templates.md](custom-templates.md).

Bundled output ids for overrides are listed in
`templates/python/src/{db,http/**}/outputs.json` and
`templates/typescript/src/http/**/outputs.json`.

## Output root

All generated paths are relative to:

```text
build/smithy/source/smithplates/
```

For example, `sourceOutputDir: "src/generated"` writes under:

```text
build/smithy/source/smithplates/src/generated/
```

### Namespace-aware layout

Generated source and test artifacts are placed under the Smithy namespace of the `@sqlService` or `@httpService` shape:

```text
<sourceOutputDir>/<smithy namespace path>/<artifact file>
<testOutputDir>/<smithy namespace path>/<artifact file>
```

For a service in namespace `com.example.inventory`, bundled Python SQL output looks like:

```text
src/generated/com/example/inventory/{{serviceFileName}}_protocol.py
src/generated/com/example/inventory/models/{{serviceFileName}}_models.py
src/generated/com/example/inventory/sqlite/{{serviceFileName}}_aiosqlite.py
```

Default Python import packages mirror the Smithy namespace (for example `generated.com.example.inventory`). Explicit `packageName` overrides use the configured value as-is; filesystem paths still follow the Smithy namespace.

Migration SQL files remain under each dialect `migrationLocation` and are not namespace-prefixed.

## Generated output ownership

Smithplates writes into Smithy's build output tree. A consumer project can choose one of three ownership patterns:

- Copy or sync generated files into a committed source tree, as the Python petstore example does.
- Leave generated files under `build/smithy/source/smithplates/` and include that tree in local tooling paths.
- Regenerate in CI and compare against committed output to catch drift.

Whichever pattern you choose, keep hand-written application code outside generated directories.

See [Integration](integration.md) for a combined SQL and HTTP configuration walkthrough.
