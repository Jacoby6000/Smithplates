# Codegen Core

Read this when working on language-neutral codegen: the planner, output decks,
`TemplateView`, naming strategies, or validation errors shared by SQL and HTTP
renderers.

## Role

`smithplates-codegen-core` is the language-neutral foundation shipped by the
closed `#34` epic (`#35`–`#42`). It owns:

* **Domain ADTs** — `NeutralType`, `Model[A]`, `ModelSet[A]`, `ServiceModel`,
  `OperationModel`, metadata wrappers (`ModelMeta`, `ServiceMeta`, `OperationMeta`)
* **Type analysis** — `TypeResolver` (alias chasing), `TypeUsageAnalyzer`
  (deterministic `usedTypes` for imports)
* **Planning** — `CodegenOutput` decks, `CodegenPlanner`, `PathTemplate`,
  `SmithyBinding`, `BindingFilter`, `BindingGroup`
* **Rendering contract** — `TemplateView`, `TemplateRenderer`, `Conventions` /
  `NamingStrategy`, `TypeRenderer` / `ConfigurableTypeRenderer`
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

**Template rendering** is fully on neutral `TemplateView` adapters:

| Area | Adapter |
|------|---------|
| HTTP models | `HttpNeutralModelTemplateAttributes` + `TypeRenderer` |
| HTTP service utilities | `HttpNeutralServiceTemplateAttributes` |
| HTTP route groups / clients | `HttpNeutralRouteGroupTemplateAttributes` |
| SQL db templates | `SqlNeutralServiceTemplateAttributes` (`ServiceModel[SqlServiceMeta, SqlOperationMeta]` + enriched `SqlMeta` on `usedTypes`) |

Python-only formatting helpers (for example `pythonTupleOfPairs` in the HTTP SSP preamble) live in templates, not in legacy Scala view types. `SqlCodegenUuidTypeNames` is a small context-build helper that populates `SqlServiceMeta.uuidTypeNames` during enrichment — not a parallel template-view bridge.

Extraction still uses feature-specific IR alongside core extractors (for example HTTP binding facts via `HttpCoreMetaBuilder`, SQL schema/query IR for DDL and rendered SQL). That is separate from the template `TemplateView` surface documented here.

## Key packages

| Package | Contents |
|---------|----------|
| `codegen.core` | `NeutralType`, models, `TypeResolver`, `TypeUsageAnalyzer`, `SystemValidator` |
| `codegen.core.planning` | `CodegenPlanner`, `CodegenOutput`, `TemplateView`, `ResolvedArtifact`, `SmithyBinding` |
| `codegen.core.planning.config` | `CodegenOutputDeck`, `CodegenOutputDeckLoader`, JSON decoders |
| `codegen.core.strategy` | `NamingStrategy`, `Conventions`, `TypeRenderer`, `ConfigurableTypeRenderer`, `RenderContext` |
| `codegen.core.strategy.config` | `TypeSyntaxConfig`, `LanguageBaseConfig`, `LanguageBaseConfigLoader` |

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
