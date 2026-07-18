# Custom templates

Smithplates renders target-language artifacts with Scalate SSP templates. Bundled templates ship for **Python** (SQL + HTTP server/client) and **TypeScript** (HTTP client); other languages or custom layouts provide an explicit `templateDirectory`.

Every template directory pairs the templates with an **`outputs.json` deck** that declares which files to generate. When you point a feature at a custom `templateDirectory`, that directory must contain both the templates **and** an `outputs.json` (see [Output deck](#output-deck-outputsjson) below).

## SQL templates

Configure SQL templates per language target:

```json
{
  "python": {
    "sourceOutputDir": "src/generated",
    "testOutputDir": "tests",
    "sql": {
      "templateDirectory": "classpath:custom-templates/python/src/db"
    }
  }
}
```

Bundled Python uses `classpath:` templates under `templates/python/src/db/` (with `templates/python/src/db/outputs.json`). Non-bundled languages must provide `templateDirectory` and an `outputs.json` deck beside the templates.

The templates that must exist are exactly the `.ssp` templates referenced by the deck for the enabled dialects. The bundled deck, for example, requires:

- Shared: model and protocol templates.
- SQLite-enabled: SQLite service, migration, transaction, and test templates.
- Postgres-enabled: Postgres service, migration, transaction, test, and testcontainers-stub templates.
- Shared-only (no enabled dialects): only shared model and protocol templates.

## HTTP templates

Configure HTTP templates under each language's `server` (and/or `client`) settings:

```json
{
  "python": {
    "sourceOutputDir": "src/generated",
    "testOutputDir": "tests",
    "http": {
      "server": {
        "webFramework": "fastapi",
        "templateDirectory": "classpath:custom-templates/python/src/http/server"
      }
    }
  }
}
```

Bundled Python/FastAPI templates live under `templates/python/src/http/`, each with its own `outputs.json` deck: `http/server/outputs.json`, `http/client/outputs.json`, and `http/models/outputs.json`.

Bundled TypeScript client templates live under `templates/typescript/src/http/` with decks for `client/` and `models/` (`httpLibrary` variants `fetch` / `axios`).

## Output deck (`outputs.json`)

The output deck is a language-neutral JSON file that lists the artifacts a feature emits. The engine derives the deck path from the resolved template directory: `<templateDirectory>/outputs.json`. There is no per-language logic in the plugin — the same rules apply to every language, and only the JSON data differs. The bundled Python and TypeScript decks are the easiest starting points to copy and adapt.

### Top-level shape

```jsonc
{
  "shared":   [ /* outputs emitted for every enabled configuration */ ],
  "variants": {                       // optional; default {}
    "fastapi": [ /* emitted only when the "fastapi" web framework is enabled */ ],
    "sqlite":  [ /* emitted only when the "sqlite" dialect is enabled */ ]
  }
}
```

- `shared` (optional, default `[]`) — always emitted.
- `variants` (optional, default `{}`) — keyed by the enabled framework/library/dialect (`fastapi`, `httpx`, `fetch`, `axios`, `sqlite`, `postgres`, …). The emitted set is `shared` plus every enabled variant.

### Output objects

Each output has common keys plus type-specific keys.

| key            | required | notes |
|----------------|----------|-------|
| `id`           | yes      | Stable, unique id, e.g. `python.http.server.app_factory`. |
| `artifactKind` | yes      | `"src"` or `"test"` — selects the source vs test output root. |
| `type`         | no       | `"template"` (default) or `"static"`. |
| `overrides`    | no       | `id` of another output this one replaces. |

**`type: "template"`** (rendered, the common case):

| key          | required | notes |
|--------------|----------|-------|
| `template`   | yes      | Template path relative to the template directory. A `*.ssp` file is rendered; any other extension (e.g. a runtime `.py`) is copied verbatim. |
| `outputPath` | yes      | Output path pattern with `{{placeholder}}` tokens (see below). |
| `binding`    | yes      | Which Smithy shapes drive this output (see below). |

**`type: "static"`** (copied without rendering):

| key          | required | notes |
|--------------|----------|-------|
| `filePath`   | yes      | Resource to copy. |
| `copyToPath` | yes      | Output path pattern (same placeholder rules). |

### Bindings

`binding.type` selects the shapes an output is expanded over:

```jsonc
{ "type": "service" }   // once per service
{ "type": "once" }      // exactly one output for the whole run
{ "type": "operation", "filters": ["tagged"],        "groupBy": "tag" }
{ "type": "model",     "filters": [{ "kind": "enum" }], "groupBy": "none" }
```

- `filters` (optional, default matches everything) — all atoms must match:
  - `"all"`, `"tagged"`, `"untagged"`
  - `{ "kind": "structure" | "union" | "enum" | "alias" }` (model bindings only)
- `groupBy` (optional, default `"none"`):
  - `"none"` — one output per matching shape
  - `"all"` — a single output over all matching shapes
  - `"tag"` — one output per distinct tag

### Output path placeholders

`outputPath` / `copyToPath` use `{{token}}` placeholders resolved from the language's naming conventions. Which tokens are available depends on the binding; a token left unresolved fails generation.

- Always: `{{rootNamespaceDir}}`
- Namespace-scoped: `{{smithyNamespaceDir}}` (`a.b` → `a/b`), `{{packageName}}`
- `service` bindings: `{{serviceName}}`, `{{serviceClassName}}`, `{{serviceFileName}}`, `{{serviceModuleName}}`, `{{serviceNamespace}}`, `{{serviceShapeId}}`, `{{serviceVersion}}`
- `model` bindings: `{{modelName}}`, `{{modelClassName}}`, `{{modelFileName}}`, `{{modelNamespace}}`, `{{modelShapeId}}`
- `operation` bindings: `{{operationName}}`, `{{operationClassName}}`, `{{operationFileName}}`, `{{operationNamespace}}`, `{{operationShapeId}}`
- `groupBy: "tag"`: `{{tagName}}`

### Overrides

Set `overrides` to another output's `id` to replace it — the overridden output is dropped and the overriding one is kept. This lets a variant swap a `shared` default for a framework-specific implementation without redefining the rest of the deck. Duplicate ids, self-overrides, and overrides pointing at an unknown id are rejected.

### Validation & strictness

- A missing `outputs.json` for a configured template directory is reported as `missing codegen output deck: <path>`.
- If the deck references a `.ssp` template that does not exist in the template directory, generation fails with `missing required templates: …`.
- Enabling a framework/library/dialect that has no matching variant fails with `unknown deck variant '<key>'`.
- Decoding is strict: unknown JSON keys and unsupported enum values are rejected with a message listing what is allowed.

### Example

```jsonc
{
  "shared": [
    {
      "id": "python.http.server.app_factory",
      "artifactKind": "src",
      "template": "app_factory.ssp",
      "outputPath": "{{smithyNamespaceDir}}/app.py",
      "binding": { "type": "once" }
    },
    {
      "id": "python.http.models.enum",
      "artifactKind": "src",
      "template": "models/enum.ssp",
      "outputPath": "{{smithyNamespaceDir}}/{{modelFileName}}.py",
      "binding": { "type": "model", "filters": [ { "kind": "enum" } ], "groupBy": "none" }
    }
  ],
  "variants": {
    "fastapi": [
      {
        "id": "python.http.server.fastapi.route_group_routes",
        "artifactKind": "src",
        "template": "fastapi/routes.ssp",
        "outputPath": "{{smithyNamespaceDir}}/apis/{{tagName}}_api.py",
        "binding": { "type": "operation", "filters": [ "tagged" ], "groupBy": "tag" }
      }
    ]
  }
}
```

## Template authoring

Templates may use shared fragments and generated attributes supplied by the renderer. Contributor-facing details live in [Template authoring](../contributing/template-authoring.md), including golden tests, bundled resources, and precompilation.

## Publishing behavior

Bundled templates are ahead-of-time compiled into renderer jars. Consumers still depend only on `com.jacoby6000:smithplates-plugin`; Maven resolves the renderer jars transitively.
