# Configuration

Smithplates settings live under the `smithplates` plugin key in `smithy-build.json`.

```json
{
  "plugins": {
    "smithplates": {
      "<language>": {
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "sql": {},
        "http": {}
      }
    }
  }
}
```

Each language entry requires `sourceOutputDir` and `testOutputDir` once at the language level. SQL and HTTP codegen for that language share those output roots. At least one language entry must contain `sql` or `http`. Settings validation accumulates errors and reports them at the Smithy build plugin boundary.

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
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "sql": {
          "sqlite": {
            "enable": true,
            "migrationLocation": "db/migrations/sqlite"
          }
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
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "http": {
          "server": {
            "webFramework": "fastapi"
          },
          "client": {
            "httpLibrary": "httpx"
          }
        }
      },
      "typescript": {
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "http": {
          "client": {
            "httpLibrary": "fetch"
          }
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
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "sql": {
          "sqlite": {
            "enable": true,
            "migrationLocation": "db/migrations/sqlite"
          },
          "rootNamespace": "generated"
        },
        "http": {
          "rootNamespace": "generated",
          "server": {
            "webFramework": "fastapi",
            "packageName": "generated.api"
          }
        }
      }
    }
  }
}
```

## Language-level output directories

| Field | Required | Meaning |
|-------|----------|---------|
| `sourceOutputDir` | Yes | Base output directory for generated source artifacts (SQL and HTTP). |
| `testOutputDir` | Yes | Base output directory for generated test artifacts (SQL and HTTP). |
| `enableExternalTemplates` | No; default `false` | When `true`, allows `additionalTemplatesDirectory` to reference SSP templates outside the bundled classpath (see [Custom outputs](#custom-codegen-outputs)). Emits a build warning because external SSP executes arbitrary Scala at build time. |

Do not set `sourceOutputDir` or `testOutputDir` under `sql`, `http.server`, or `http.client`; configure them once on `smithplates.<language>`.

## `smithplates.<language>.sql`

SQL configuration has two independent concerns:

- Dialect keys (`sqlite`, `postgres`) control build-time migration DDL and dialect-specific generated implementations.
- `templateDirectory`, `rootNamespace`, and `packageName` control template selection and Python import packages for `@sqlService` artifacts in that language.

```json
{
  "python": {
    "sourceOutputDir": "src/generated",
    "testOutputDir": "tests",
    "sql": {
      "sqlite": {
        "enable": true,
        "migrationLocation": "db/migrations/sqlite"
      },
      "postgres": {
        "enable": true,
        "migrationLocation": "db/migrations/postgres"
      },
      "rootNamespace": "generated"
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

When a language `sql` block is configured with no enabled dialects, Smithplates renders only shared model and protocol artifacts. That shared output is dialect-free: it does not require a query renderer, DDL renderer, migration location, or dialect-specific template.

## `smithplates.<language>.http`

HTTP configuration lives beside SQL under the language entry and contains `server` and/or `client` objects:

```json
{
  "python": {
    "sourceOutputDir": "src/generated",
    "testOutputDir": "tests",
    "http": {
      "server": {
        "webFramework": "fastapi",
        "packageName": "generated.api"
      },
      "client": {
        "httpLibrary": "httpx",
        "packageName": "generated.api_client"
      }
    }
  }
}
```

| Field | Required | Meaning |
|-------|----------|---------|
| `webFramework` | No; default `fastapi` | Web framework for generated server artifacts. Python/FastAPI is the bundled server today. |
| `httpLibrary` | No; default `httpx` for Python | HTTP client library. Bundled values: `httpx` (Python), `fetch` or `axios` (TypeScript). |
| `packageName` | No | Override the derived import package for server or client output exactly. When omitted, packages include the Smithy service namespace. |
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
    "sourceOutputDir": "src/generated",
    "testOutputDir": "tests",
    "sql": {
      "sqlite": { "enable": true, "migrationLocation": "db/migrations/sqlite" },
      "additionalTemplatesDirectory": "classpath:my-templates/sql"
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
