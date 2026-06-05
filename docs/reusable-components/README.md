# Reusable documentation components

Reusable documentation fragments live here. Markdown files embed them via `scripts/sync_reusable_components.py`.

The sync script discovers components automatically from this directory (every file except this README). Embed targets are discovered by searching the repository for marker comments that use the component filename.

| Component | Marker | Fence |
|-----------|--------|-------|
| [`architecture-pipeline.mmd`](architecture-pipeline.mmd) | `architecture-pipeline.mmd` | `mermaid` |

To embed a component in a Markdown file:

```html
<!-- architecture-pipeline.mmd:start -->
<!-- architecture-pipeline.mmd:end -->
```

Edit a component, then run:

```bash
scripts/sync_reusable_components.py
```

Pre-commit runs `scripts/sync_reusable_components.py --check` when reusable components, the sync script, or Markdown embed targets change.
