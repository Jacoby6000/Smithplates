# Docs maintenance

Smithplates keeps user docs and contributor docs separate:

- `docs/usage/` is for consumers of the published plugin.
- `docs/contributing/` is for people changing Smithplates itself.

When behavior changes, update the docs that match the affected audience.

## User docs

Update `docs/usage/` when changing:

- `smithy-build.json` configuration;
- generated output paths;
- SQL or HTTP modeling conventions;
- runtime behavior of generated artifacts;
- supported languages, frameworks, dialects, or templates;
- examples users are expected to copy.

## Contributor docs

Update `docs/contributing/` when changing:

- module boundaries;
- renderer or extractor responsibilities;
- validation style;
- test commands or CI behavior;
- release or publish workflows;
- template precompilation;
- reusable docs components.

## Reusable components

Shared diagrams live under `docs/reusable-components/`. Edit the source component, then refresh embedded copies:

```bash
python scripts/sync_reusable_components.py
```

Pre-commit checks verify reusable component sync when relevant files change.

## Avoid drift

- Keep `README.md`, `docs/README.md`, and section indexes short and navigational.
- Prefer one canonical explanation for a behavior, then link to it.
- Keep roadmap items out of current-behavior guides unless they are clearly labeled as limitations or future work.
- Treat `example/python/` as the canonical full-stack consumer reference.

## Change checklist

Before merging behavior changes, ask:

| Change | Docs to check |
|--------|---------------|
| New or changed plugin setting | `docs/usage/configuration.md`, `docs/usage/integration.md`, relevant settings specs |
| SQL modeling or generated DB behavior | `docs/usage/sql-plugin.md`, `docs/contributing/sql-architecture.md`, `modules/smithplates-plugin/README.md` |
| HTTP modeling or generated API behavior | `docs/usage/http-plugin.md`, `docs/contributing/http-architecture.md`, OpenAPI docs if transforms are affected |
| New bundled language or client library | `docs/usage/limitations.md`, `docs/usage/configuration.md`, `docs/usage/http-plugin.md`, `templates/README.md`, `example/README.md` |
| Template layout or bundled artifacts | `docs/usage/custom-templates.md`, `docs/contributing/template-authoring.md`, `templates/README.md` when relevant |
| Example workflow or generated config | `docs/usage/examples.md`, `example/README.md`, language example READMEs, rendered `smithy-build.json` files |
| Test command, CI target, or harness behavior | `CONTRIBUTING.md`, `docs/contributing/testing.md`, harness README files |
| Public support boundary or future work | `docs/usage/limitations.md`, `docs/contributing/roadmap.md` |

If a behavior is documented in more than one place, choose one page as the canonical explanation and make other pages link to it.
