# Codegen Core

Read this when working on language-neutral codegen: the planner, output decks,
`TemplateView`, naming strategies, or validation errors shared by SQL and HTTP
renderers.

## Role

`smithplates-codegen-core` is the language-neutral foundation for the #34 epic.
It owns:

* **Domain ADTs** — `NeutralType`, `Model[A]`, `ModelSet[A]`, `ServiceModel`,
  `OperationModel`, metadata wrappers (`ModelMeta`, `ServiceMeta`, `OperationMeta`)
* **Type analysis** — `TypeResolver` (alias chasing), `TypeUsageAnalyzer`
  (deterministic `usedTypes` for imports)
* **Planning** — `CodegenOutput` decks, `CodegenPlanner`, `PathTemplate`,
  `SmithyBinding`, `BindingFilter`, `BindingGroup`
* **Rendering contract** — `TemplateView`, `TemplateRenderer`, `Conventions` /
  `NamingStrategy`
* **Validation** — `SystemValidator`, `CodegenValidationError` hierarchy
  (`CodegenValidated` / `ValidatedNel`)

No target-language syntax, no Smithy SDK types, and no dependency on SQL/HTTP
renderer modules.

## Data flow (current cutover state)

```
Smithy model
  → smithplates-smithy-neutral (NeutralType lowering)
  → feature IR adapters (#36): HttpCoreModelExtractor / SqlCoreModelExtractor
  → ModelSet[Meta] + ServiceModel list
  → plugin loads outputs.json deck (+ optional consumer additional deck)
  → CodegenPlanner.plan(outputs, models, services, settings, templateRenderer)
  → List[ResolvedArtifact]
  → feature renderer writes Smithy build manifest
```

**Template renderers** still bridge a subset of bindings to legacy SSP views:

| Area | Neutral today | Legacy bridge (pending template migration) |
|------|---------------|--------------------------------------------|
| HTTP models (`structure`/`union`/`enum` bindings) | `TemplateView` + `HttpNeutralModelTemplateAttributes` | — |
| HTTP service utilities (`model_validation`, `api_response`, `app_services`, `client_registry`, `client_response`, `api_exceptions`, `api_exception_handler`, `app_factory`, `apis/__init__`, `clients/__init__`) | `TemplateView` + `HttpNeutralServiceTemplateAttributes` | — |
| HTTP server/client route-group and wiring templates | planner + deck | `HttpCodegenTemplateView` via `HttpServiceCodegenRenderer.internal.HttpPlannerTemplateRenderer` |
| SQL db templates (all bindings) | planner + deck | `ServiceTemplateView` / `SqlCodegenServiceContext` via `SqlServiceCodegenRenderer.internal.SqlPlannerTemplateRenderer` |

Removing the legacy bridges requires migrating the remaining SSP templates off
stringly type names and legacy import helpers onto `ctx.conventions` and
`ctx.usedTypes`.

## Key packages

| Package | Contents |
|---------|----------|
| `codegen.core` | `NeutralType`, models, `TypeResolver`, `TypeUsageAnalyzer`, `SystemValidator` |
| `codegen.core.planning` | `CodegenPlanner`, `CodegenOutput`, `TemplateView`, `ResolvedArtifact`, `SmithyBinding` |
| `codegen.core.planning.config` | `CodegenOutputDeck`, `CodegenOutputDeckLoader`, JSON decoders |
| `codegen.core.strategy` | `NamingStrategy`, `Conventions`, `ConfigurableTypeRenderer` |

## Output decks

Bundled and consumer decks are JSON (`outputs.json`) decoded into
`CodegenOutputDeck` — not hardcoded Scala artifact lists. See
[`codegen-output-deck.md`](codegen-output-deck.md) and
[`plugin-consumer-config.md`](plugin-consumer-config.md).

`CodegenPlanner.internal.mergeOutputs` applies consumer `overrides` by id and
rejects duplicate resolved paths at plan time (`DuplicateResolvedOutputPath`).

## Validation surface

`SystemValidator` runs after feature extraction. Plugin config validation
(`ConsumerCodegenOutputValidator`, language-target template validators) runs
before Smithy build codegen. Path placeholder expansion needs the loaded model,
so duplicate **resolved** output paths are detected during `CodegenPlanner.plan`,
not during `smithy-build.json` parsing.

## Related modules

* [`module-layout.md`](module-layout.md) — full `modules/` graph and precompilation
* [`docs/contributing/architecture.md`](../docs/contributing/architecture.md) —
  contributor-facing pipeline overview
