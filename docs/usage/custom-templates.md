# Custom templates

Smithplates renders target-language artifacts with Scalate SSP templates. Bundled templates ship for **Python** (SQL + HTTP server/client) and **TypeScript** (HTTP client); other languages or custom layouts provide an explicit `templateDirectory`.

Every template directory pairs the templates with an **`outputs.json` deck** that declares which files to generate. When you point a feature at a custom `templateDirectory`, that directory must contain both the templates **and** an `outputs.json` (see [Output deck](#output-deck-outputsjson) below).

There are two consumer extension modes:

| Mode | Setting | Effect |
|------|---------|--------|
| **Append / override** | `additionalTemplatesDirectory` on `sql`, `http.server`, or `http.client` | Consumer deck is appended to the bundled deck; `"overrides": "<bundled-id>"` replaces a bundled output by id |
| **Full replacement** | `templateDirectory` | Replaces the bundled deck entirely; the custom root must ship a complete `outputs.json` |

Filesystem (non-classpath) SSP directories also require language-level `enableExternalTemplates: true` and emit a build warning because SSP executes arbitrary Scala at build time. Full settings detail: [Configuration — Custom codegen outputs](configuration.md#custom-codegen-outputs).

## Tutorial: append a custom artifact

This walkthrough adds one extra file next to bundled SQL output without replacing the whole deck.

### 1. Create a small template tree

```text
my-templates/sql/
  outputs.json
  EXTRA_README.md
```

`EXTRA_README.md` can be plain text — non-`.ssp` paths listed as `type: "template"` are copied verbatim:

```markdown
# Companion note

This file was emitted by a consumer `additionalTemplatesDirectory` deck.
```

### 2. Declare the deck

`my-templates/sql/outputs.json`:

```json
{
  "shared": [
    {
      "id": "consumer.sql.extra_readme",
      "artifactKind": "src",
      "template": "EXTRA_README.md",
      "outputPath": "{{smithyNamespaceDir}}/EXTRA_README.md",
      "binding": { "type": "once" }
    }
  ]
}
```

Pick a unique `id` that does not collide with bundled ids in [`templates/python/src/db/outputs.json`](../../templates/python/src/db/outputs.json). Use a `.ssp` template and a `service` / `model` binding when you need generated content from the IR — copy patterns from the bundled deck.

### 3. Point the language SQL block at it

Classpath packaging (JAR / Smithy resources):

```json
"sql": {
  "sqlite": { "enable": true, "migrationLocation": "db/migrations/sqlite" },
  "additionalTemplatesDirectory": "classpath:my-templates/sql"
}
```

Filesystem path during local builds:

```json
"python": {
  "enableExternalTemplates": true,
  "sql": {
    "sqlite": { "enable": true, "migrationLocation": "db/migrations/sqlite" },
    "additionalTemplatesDirectory": "/absolute/or/repo-relative/my-templates/sql",
    "outputs": [
      { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
    ]
  }
}
```

`enableExternalTemplates: true` is required for non-classpath SSP and prints a security warning — SSP can run arbitrary Scala at build time. Verbatim non-`.ssp` copies still go through the same directory setting.

### 4. Override a bundled file (optional)

To replace an existing artifact, set `"overrides": "<bundled-id>"` on your output (for example `"python.sql.db.service_protocol"`). The bundled entry is dropped; yours is kept. Duplicate resolved output paths still fail at plan/render time.

### 5. Full deck replacement

When you need a new language or an entirely custom artifact set, set `templateDirectory` to a root that ships a **complete** `outputs.json` (not an append). Copy a bundled deck as a starting point and delete what you do not need.

Golden coverage for append/override/external flows lives under `templates/python/tests/sql-additional-templates-*` and related HTTP cases.

## Inspect applied Smithy traits

SSP templates can inspect effective traits on every service, operation, model,
and model member represented by the HTTP or SQL neutral IR. Unknown consumer
traits do not need a Smithplates trait class.

```scala
<%@ import val ctx: com.jacoby6000.smithplates.http.service.renderer.HttpNeutralServiceTemplateAttributes.ServiceView %>
<%
val customTrait = ctx.subject.meta.traits.find { applied =>
  applied.id.namespace == "example.codegen" && applied.id.name == "custom"
}
%>
```

`AppliedTrait` exposes the full trait `ModelId`, a `synthetic` flag, and a
recursive `SmithyNodeValue`. Object members and applied traits have deterministic
ordering; arrays retain Smithy order and numbers are exposed as canonical Smithy
number strings. Model templates can inspect `ctx.subject.meta.traits`, while
structure fields, union variants, and enum values expose their own `traits`
collections.

This surface contains effective traits returned by Smithy `getAllTraits`,
including mixins and external `apply` statements. It is limited to shapes and
members represented by the selected HTTP or SQL extraction closure; named
collections and other shapes flattened into `NeutralType` are not separately
available to templates.

## SQL templates

Configure SQL templates per language target:

```json
{
  "python": {
    "sql": {
      "templateDirectory": "classpath:custom-templates/python/src/db",
      "outputs": [
        { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
      ]
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
    "http": {
      "server": {
        "webFramework": "fastapi",
        "templateDirectory": "classpath:custom-templates/python/src/http/server"
      },
      "outputs": [
        { "sourceOutputDir": "src/generated", "testOutputDir": "tests" }
      ]
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
  "defaultVariant": "fastapi",          // optional; used when selection is omitted
  "variants": {                       // optional; default {}
    "fastapi": [ /* emitted only when the "fastapi" web framework is enabled */ ],
    "sqlite":  [ /* emitted only when the "sqlite" dialect is enabled */ ]
  }
}
```

- `shared` (optional, default `[]`) — always emitted.
- `defaultVariant` (optional) — variant selected when the consumer omits the corresponding framework or library setting. It must name a key in `variants`.
- `variants` (optional, default `{}`) — keyed by the enabled framework/library/dialect (`fastapi`, `httpx`, `httpx2`, `fetch`, `axios`, `sqlite`, `postgres`, …). The emitted set is `shared` plus every enabled variant.

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

Consumer-deck static outputs (`CodegenStaticOutput` from `additionalTemplatesDirectory`) are **not wired yet** — only SSP / non-`.ssp` *template* bindings are rendered from additional decks today. Bundled decks may still copy non-`.ssp` files listed as `type: "template"` (verbatim copy when the path does not end in `.ssp`). Use full `templateDirectory` replacement when a custom language needs static support files in the deck until consumer static copy lands.

### SQL enum side path

Bundled Python SQL `enum` / `intEnum` artifacts are still rendered by a Scala side path (`string_enum` / `int_enum` templates) rather than `outputs.json` model bindings. HTTP enums *are* deck-driven (`models/enum.ssp`). When authoring a custom SQL deck, do not assume enum files appear just because a model binding exists — mirror the current side path or wait for a future deck cutover.

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

Set `overrides` to another output's `id` to replace it — the overridden output is dropped and the overriding one is kept. This lets a variant swap a `shared` default for a framework-specific implementation without redefining the rest of the deck. The same mechanism works across a bundled deck and an `additionalTemplatesDirectory` deck. Duplicate ids, self-overrides, and overrides pointing at an unknown id are rejected.

Duplicate **resolved output paths** among the merged deck fail at **codegen (plan/render) time**, not during `smithy-build.json` validation — resolved paths depend on Smithy namespaces, tags, and enabled dialects.

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
