# Roadmap

This page is for contributor-facing planning. User-facing docs should describe shipped behavior first and link to [Limitations](../usage/limitations.md) for boundaries.

## Current focus

- Keep SQL and HTTP docs aligned with shipped behavior.
- Make the Python petstore example the canonical full-stack reference.
- Keep shared-only SQL codegen dialect-free for model/protocol output.
- Split broad integration docs into task-oriented usage pages.
- Split contributor docs by change path: SQL, HTTP, templates, testing, release, and docs maintenance.

## Known future work

- Diff-based incremental migrations from Smithy model changes.
- Additional SQL dialects and driver patterns.
- Additional language targets beyond bundled Python.
- Additional HTTP frameworks beyond bundled FastAPI.
- Stronger generated HTTP execution tests beyond render-focused golden cases.

## Roadmap hygiene

When adding roadmap work:

1. Keep the user guide clear about what exists today.
2. Add or update contributor architecture notes for the intended design.
3. Add acceptance criteria to the related issue.
4. Identify the test layer that will prove the change.
5. Update examples only after the behavior is implemented.
