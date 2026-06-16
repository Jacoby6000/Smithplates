# Configuration

Smithplates settings live under the `smithplates` plugin key in `smithy-build.json`.

```json
{
  "plugins": {
    "smithplates": {
      "sql": {},
      "http": {}
    }
  }
}
```

At least one of `sql` or `http` must be present. Settings validation accumulates errors and reports them at the Smithy build plugin boundary.

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
      "sql": {
        "sqlite": {
          "enable": true,
          "migrationLocation": "db/migrations/sqlite"
        },
        "languageTargets": {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests"
          }
        }
      }
    }
  }
}
```

### HTTP only

Use this when a project only wants generated FastAPI wiring from `@httpService` models:

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
      "http": {
        "python": {
          "server": {
            "webFramework": "fastapi",
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "packageName": "generated.api"
          }
        }
      }
    }
  }
}
```

### SQL and HTTP together

Use the same `smithplates` block with both `sql` and `http`. Keep SQL and HTTP Smithy namespaces separate, then bridge them in hand-written application code. A combined copy/paste example:

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
      "sql": {
        "sqlite": {
          "enable": true,
          "migrationLocation": "db/migrations/sqlite"
        },
        "languageTargets": {
          "python": {
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests"
          }
        }
      },
      "http": {
        "python": {
          "server": {
            "webFramework": "fastapi",
            "sourceOutputDir": "src/generated",
            "testOutputDir": "tests",
            "packageName": "generated.api"
          }
        }
      }
    }
  }
}
```

## `smithplates.sql`

SQL configuration has two independent concerns:

- Dialect keys (`sqlite`, `postgres`) control build-time migration DDL and dialect-specific generated implementations.
- `languageTargets` controls generated source and test artifacts for `@sqlService` models.

```json
{
  "sql": {
    "sqlite": {
      "enable": true,
      "migrationLocation": "db/migrations/sqlite"
    },
    "postgres": {
      "enable": true,
      "migrationLocation": "db/migrations/postgres"
    },
    "languageTargets": {
      "python": {
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests"
      }
    }
  }
}
```

| Field | Required | Meaning |
|-------|----------|---------|
| `sqlite.enable` / `postgres.enable` | No; default `false` | Enables that dialect's DDL migration files and dialect-specific generated implementation artifacts. |
| `sqlite.migrationLocation` / `postgres.migrationLocation` | Yes when `enable` is `true` | Directory for versioned migration SQL files, such as `db/migrations/sqlite`. The value must be a directory path, not a `.sql` file. |
| `languageTargets.<language>.sourceOutputDir` | Yes | Base output directory for generated source artifacts. |
| `languageTargets.<language>.testOutputDir` | Yes | Base output directory for generated test artifacts. |
| `languageTargets.<language>.templateDirectory` | Required for non-bundled languages | Classpath template root. Bundled Python uses the packaged templates by default. |

When `languageTargets` is configured with no enabled dialects, Smithplates renders only shared model and protocol artifacts. That shared output is dialect-free: it does not require a query renderer, DDL renderer, migration location, or dialect-specific template.

## `smithplates.http`

HTTP configuration is organized by language id. Each language entry contains a `server` object:

```json
{
  "http": {
    "python": {
      "server": {
        "webFramework": "fastapi",
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "packageName": "generated.api"
      }
    }
  }
}
```

| Field | Required | Meaning |
|-------|----------|---------|
| `webFramework` | No; default `fastapi` | Web framework for generated server artifacts. Python/FastAPI is the bundled implementation today. |
| `sourceOutputDir` | Yes | Base output directory for generated HTTP source artifacts. |
| `testOutputDir` | Yes | Base output directory for generated HTTP test artifacts. |
| `packageName` | No; default `generated.api` | Python import root for generated HTTP modules. |
| `templateDirectory` | Required for non-bundled languages | Classpath template root for custom HTTP templates. |

## Output root

All generated paths are relative to:

```text
build/smithy/source/smithplates/
```

For example, `sourceOutputDir: "src/generated"` writes under:

```text
build/smithy/source/smithplates/src/generated/
```

## Generated output ownership

Smithplates writes into Smithy's build output tree. A consumer project can choose one of three ownership patterns:

- Copy or sync generated files into a committed source tree, as the Python petstore example does.
- Leave generated files under `build/smithy/source/smithplates/` and include that tree in local tooling paths.
- Regenerate in CI and compare against committed output to catch drift.

Whichever pattern you choose, keep hand-written application code outside generated directories.

See [Integration](integration.md) for a combined SQL and HTTP configuration walkthrough.
