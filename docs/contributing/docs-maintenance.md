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
