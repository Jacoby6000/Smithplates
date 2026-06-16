# Template authoring

Smithplates target-language output is rendered with Scalate SSP templates. Template authors usually work in `templates/`, golden fixtures, and language harnesses.

## Layout

```text
templates/
  python/
    src/
      db/
      http/
    tests/
      <case-name>/
        smithy/smithy-files.smithy
        smithy-build.json
        expected/
language-test-harnesses/
  python/
```

Bundled templates are packaged as compile resources and precompiled into renderer jars.

## SQL templates

Bundled SQL service templates live under:

```text
templates/python/src/db/
```

Shared DB artifacts include model and protocol templates. Dialect-specific trees add SQLite and Postgres implementations, migration services, transaction helpers, generated tests, and stubs where needed.

When no SQL dialect is enabled, shared-only rendering is dialect-free and requires only shared model/protocol templates.

## HTTP templates

Bundled HTTP service templates live under:

```text
templates/python/src/http/
```

These render FastAPI route modules, protocol base classes, app wiring, response helpers, problem-detail helpers, and model artifacts.

## Fragments

Bundled Python templates organize reusable snippets under `fragments/`. Include or render fragments with Scalate calls such as:

```ssp
<% include("fragments/...") %>
<% render("fragments/...", Map(...)) %>
```

Keep shared formatting, naming, imports, and type rendering in fragments when multiple templates need the same behavior.

## Validation workflow

1. Edit SSP templates or fragments.
2. Run golden render tests.
3. Refresh expected files if output changed intentionally.
4. Run language harness linters and tests.

```bash
./scripts/run-template-golden-tests.sh
sbtn 'generateGoldenTemplatesFor python <case-name>'
./language-test-harnesses/python/run-linters.sh
./language-test-harnesses/python/run-tests.sh
```

## Template precompilation

Bundled templates are ahead-of-time compiled into JVM classes during the renderer build. Runtime template engines use the same package prefix so consumer `smithy build` runs load precompiled template classes instead of invoking the Scala compiler.

See [Architecture](architecture.md#template-precompilation) for the full build-time and runtime design.
