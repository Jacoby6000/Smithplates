# Getting started

This guide is for a consumer project that wants to run the `smithplates` Smithy build plugin and inspect generated output.

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

During local Smithplates development, run `sbtn publishM2` from this repository first, then use the version printed by `sbtn print smithplatesPlugin/version`.

Consumers reference only `com.jacoby6000:smithplates-plugin`; its published transitive dependencies carry the trait IDL, renderers, and precompiled bundled templates.

## 2. Configure `smithplates`

Configure Smithplates under `plugins.smithplates`. At least one of `sql` or `http` must be present.

For SQL, enable one or more dialects for migration DDL and add `languageTargets` for generated repository artifacts:

```json
{
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

For HTTP, configure a language entry directly under `http`:

```json
{
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

See [Configuration](configuration.md) for the full settings model.

## 3. Author Smithy models

Use separate Smithy namespaces for persistence and wire contracts:

- SQL models use traits such as `@sqlTable`, `@sqlService`, and `@sqlDeriveInsert`.
- HTTP models use `@httpService`, standard Smithy `@http`, `@tags`, and optional Smithplates HTTP traits such as `@httpProblem`.

Keeping these namespaces separate lets generated repositories and generated HTTP routes evolve independently.

## 4. Run `smithy build`

Run `smithy build` from the consumer project. Smithy writes plugin artifacts under:

```text
build/smithy/source/smithplates/
```

Copy, sync, or otherwise project those files into your application layout. The [Python petstore example](../../example/python/) demonstrates this with `build-generated.sh`.

## Next reading

- [Configuration](configuration.md) for all settings and output locations.
- [SQL plugin](sql-plugin.md) for SQL traits, generated repositories, migrations, and transactions.
- [HTTP plugin](http-plugin.md) for FastAPI output, route groups, protocols, and problem details.
- [Examples](examples.md) for the full Python reference project.
