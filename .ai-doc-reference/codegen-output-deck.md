# Codegen output deck (`outputs.json`) contract

The set of artifacts a language emits for a feature (`@httpService` server/client/models,
`@sqlService` DB output) is defined by an **`outputs.json` deck** resource that lives next
to that language's templates. No artifact data is hardcoded in Scala — the composers
(`HttpServiceCodegenApiArtifacts`, `HttpClientCodegenApiArtifacts`,
`SqlServiceCodegenDbArtifacts`) only load and compose decks.

## Source of truth

This document describes the contract; the executable definition is:

- Deck shape + strict loader: [`CodegenOutputDeck`](../modules/smithplates-codegen-core/src/main/scala/com/jacoby6000/smithplates/codegen/core/planning/config/CodegenOutputDeck.scala)
- Field decoders (strict key checking): [`CodegenOutputDecoders`](../modules/smithplates-codegen-core/src/main/scala/com/jacoby6000/smithplates/codegen/core/planning/config/CodegenOutputDecoders.scala)
- Classpath resolution + cache: [`CodegenOutputDeckLoader`](../modules/smithplates-codegen-core/src/main/scala/com/jacoby6000/smithplates/codegen/core/planning/config/CodegenOutputDeckLoader.scala)
- Domain types: [`CodegenOutput`](../modules/smithplates-codegen-core/src/main/scala/com/jacoby6000/smithplates/codegen/core/planning/CodegenOutput.scala), [`Binding`](../modules/smithplates-codegen-core/src/main/scala/com/jacoby6000/smithplates/codegen/core/planning/Binding.scala)
- Output-path placeholders: [`PathTemplate`](../modules/smithplates-codegen-core/src/main/scala/com/jacoby6000/smithplates/codegen/core/planning/PathTemplate.scala)
- Planner (how outputs expand to files): [`CodegenPlanner`](../modules/smithplates-codegen-core/src/main/scala/com/jacoby6000/smithplates/codegen/core/planning/CodegenPlanner.scala)

Bundled reference decks (Python):
`templates/python/src/http/{server,client,models}/outputs.json`,
`templates/python/src/db/outputs.json`.

## Location & loading

- A deck is read from `<templateDirectory>/outputs.json`, where `templateDirectory` is the
  (language-encoding) resolved template dir — e.g. `classpath:python/src/http/server`
  → resource `python/src/http/server/outputs.json`. The `classpath:` prefix is stripped;
  the `outputs.json` filename is fixed (`CodegenOutputDeckLoader.OutputsFileName`).
- The default template dir is derived from `languageId`, so a language never appears as a
  literal in Scala. A custom `templateDirectory` **must ship its own `outputs.json`**.
- Missing deck resource → validation error `missing codegen output deck: <path>`.
  A present deck whose referenced `.ssp` templates are absent → a separate
  `missing required templates: …` error from the language-target validator.
- Decoding is **strict**: unknown keys anywhere are rejected with
  `unexpected keys: … (allowed: …)`. Invalid enum values are rejected with the allowed set.

## Top-level schema

```jsonc
{
  "shared":   [ /* CodegenOutput, always emitted */ ],
  "variants": {                      // optional; default {}
    "<key>": [ /* CodegenOutput, emitted only when <key> is enabled */ ]
  }
}
```

- `shared` (optional, default `[]`): outputs emitted for every enabled configuration.
- `variants` (optional, default `{}`): keyed groups. A caller enables keys
  (framework/library/dialect, e.g. `"fastapi"`, `"httpx"`, `"sqlite"`, `"postgres"`);
  composition = `shared ++ (enabled variants, in the order requested)`.
- Enabling a key with no matching variant → `unknown deck variant '<key>' (available: …)`.

## `CodegenOutput` object

Common keys (both output types):

| key            | required | notes |
|----------------|----------|-------|
| `id`           | yes      | Stable, globally-unique output id (e.g. `python.http.server.app_factory`). |
| `artifactKind` | yes      | `"src"` or `"test"`. Chooses source vs test output base dir. |
| `type`         | no       | `"template"` (default) or `"static"`. |
| `overrides`    | no       | `id` of another output this one replaces (see below). |

### `type: "template"` (rendered via a template, the common case)

| key          | required | notes |
|--------------|----------|-------|
| `template`   | yes      | Template path relative to the template dir. `*.ssp` is rendered through Scalate; **any other extension is copied verbatim** (runtime support files). `.ssp` is language-neutral (the Scalate extension), see `SqlServiceCodegenDbArtifacts.isRenderedTemplate`. |
| `outputPath` | yes      | Output path pattern with `{{placeholder}}` tokens (see below). |
| `binding`    | yes      | What Smithy shapes drive this output (see Bindings). |

### `type: "static"` (copy a classpath resource, no rendering)

| key          | required | notes |
|--------------|----------|-------|
| `filePath`   | yes      | Classpath resource to copy. |
| `copyToPath` | yes      | Output path pattern (same placeholder rules). |

## Bindings

`binding` selects the shapes an output is expanded over. `type` is required:

```jsonc
{ "type": "service" }                       // once per service (deduped by namespace unless path is service-scoped)
{ "type": "once" }                          // exactly one output for the whole run
{ "type": "operation", "filters": [...], "groupBy": "all" | "none" | "tag" }
{ "type": "model",     "filters": [...], "groupBy": "all" | "none" | "tag" }
```

- `filters` (optional, default `[]` ≡ `["all"]`) — every atom must match:
  - `"all"`, `"tagged"`, `"untagged"`
  - `{ "kind": "structure" | "union" | "enum" | "alias" }` (model bindings only; a
    `kind` filter on an operation binding is rejected at validation time)
- `groupBy` (optional, default `"none"`):
  - `"none"` — one output per matching shape
  - `"all"` — a single output covering all matching shapes
  - `"tag"` — one output per distinct tag

## `outputPath` / `copyToPath` placeholders

Patterns use `{{token}}`. Available tokens depend on the binding (an unresolved token
left in the final path fails planning). Values are produced by the language `Conventions`.

Always available:

| token              | meaning |
|--------------------|---------|
| `{{rootNamespaceDir}}` | configured root namespace directory |

Namespace-scoped (service/model bindings, or grouped outputs with a single namespace):

| token                 | meaning |
|-----------------------|---------|
| `{{smithyNamespaceDir}}` | Smithy namespace as a dir path (`a.b` → `a/b`) |
| `{{packageName}}`        | language package name for the namespace |
| `{{modelNamespace}}`     | raw namespace (grouped model outputs) |

Service bindings (`serviceBindings`):

`{{serviceName}}`, `{{serviceClassName}}`, `{{serviceFileName}}`, `{{serviceModuleName}}`,
`{{serviceNamespace}}`, `{{serviceShapeId}}`, `{{serviceVersion}}`.

Model bindings (`modelBindings`):

`{{modelName}}`, `{{modelClassName}}`, `{{modelFileName}}`, `{{modelNamespace}}`,
`{{modelShapeId}}`.

Operation bindings (`operationBindings`):

`{{operationName}}`, `{{operationClassName}}`, `{{operationFileName}}`,
`{{operationNamespace}}`, `{{operationShapeId}}`.

Tag-grouped bindings (`groupBy: "tag"`): `{{tagName}}`.

> Note: for a `service` binding, if `outputPath` contains no service-scoped placeholder the
> planner deduplicates to one service per namespace; include a service-scoped token to emit
> per service.

## `overrides`

`overrides` lets one output replace another (e.g. a variant output replacing a `shared`
default) by id: set `overrides` to the overridden output's `id`, and the planner drops the
overridden output, keeping the overriding one. Use it to swap a shared default for a
framework-specific implementation without duplicating the rest of the deck. Planner rules:

- duplicate `id`s across the composed set → `DuplicateOutputId`
- an output that overrides itself → `SelfOutputOverride`
- `overrides` referencing an id not in the composed set → `UnknownOutputOverride`

## Example

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
