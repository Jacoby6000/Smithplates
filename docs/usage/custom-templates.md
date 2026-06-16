# Custom templates

Smithplates renders target-language artifacts with Scalate SSP templates. Bundled Python templates are packaged in the published renderer jars; other languages or custom layouts can provide an explicit `templateDirectory`.

## SQL templates

Configure SQL templates per language target:

```json
{
  "python": {
    "sql": {
      "sourceOutputDir": "src/generated",
      "testOutputDir": "tests",
      "templateDirectory": "classpath:custom-templates/python/src/db"
    }
  }
}
```

Bundled Python uses `classpath:` templates under `templates/python/src/db/`. Non-bundled languages must provide `templateDirectory`.

Required SQL templates are derived from enabled dialects:

- Shared artifacts require model and protocol templates.
- SQLite-enabled output requires SQLite service, migration, transaction, and test templates.
- Postgres-enabled output requires Postgres service, migration, transaction, test, and testcontainers-stub templates.
- Shared-only output with no enabled dialects requires only shared model and protocol templates.

## HTTP templates

Configure HTTP templates under each language's `server` settings:

```json
{
  "python": {
    "http": {
      "server": {
        "webFramework": "fastapi",
        "sourceOutputDir": "src/generated",
        "testOutputDir": "tests",
        "templateDirectory": "classpath:custom-templates/python/src/http/server"
      }
    }
  }
}
```

Bundled Python/FastAPI templates live under `templates/python/src/http/`.

## Template authoring

Templates may use shared fragments and generated attributes supplied by the renderer. Contributor-facing details live in [Template authoring](../contributing/template-authoring.md), including golden tests, bundled resources, and precompilation.

## Publishing behavior

Bundled templates are ahead-of-time compiled into renderer jars. Consumers still depend only on `com.jacoby6000:smithplates-plugin`; Maven resolves the renderer jars transitively.
