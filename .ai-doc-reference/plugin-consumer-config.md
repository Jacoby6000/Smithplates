# Plugin Consumer Configuration

Read this when wiring `smithy-build.json` for consumers, adding language targets,
or validating plugin config in tests.

Full user documentation:
[`docs/usage/configuration.md`](../docs/usage/configuration.md). HTTP-specific
config: [`docs/usage/http-plugin.md`](../docs/usage/http-plugin.md). Trait
reference: [`modules/smithplates-plugin/README.md`](../modules/smithplates-plugin/README.md).

## Maven coordinates

Published plugin JARs are consumed by `smithy build` via Maven coordinates in
consumer `smithy-build.json` files.

## Language-first `smithplates` key

Configuration under the `smithplates` plugin key is language-first: each top-level
key is a language id such as `python`, with required `sourceOutputDir` and
`testOutputDir`, plus optional `sql` and `http` blocks.

### SQL

SQL dialect keys (`sqlite`, `postgres`) live under `smithplates.<language>.sql`
and take `enable` (default `false`) and `migrationLocation`. SQL blocks accept
optional `templateDirectory`, `rootNamespace`, and `packageName`.

Configure SQL outputs in `smithplates.<language>.sql`.

Consumers can append bundled outputs via `additionalTemplatesDirectory` on the `sql`
block (and `enableExternalTemplates` on the language entry). See
[`docs/usage/configuration.md`](../docs/usage/configuration.md#custom-codegen-outputs).

### HTTP

HTTP configuration uses `smithplates.<language>.http.server` and/or
`smithplates.<language>.http.client`.

Configure HTTP server/client outputs in
`smithplates.<language>.http.server` / `smithplates.<language>.http.client`.

Consumers can also append bundled outputs via `additionalTemplatesDirectory` on each
`sql` / `http.server` / `http.client` block (and `enableExternalTemplates` on the
language entry). See
[`docs/usage/configuration.md`](../docs/usage/configuration.md#custom-codegen-outputs).

Path collision detection for merged decks runs at codegen time (after the Smithy model
is loaded), not during plugin config validation. Consumer-deck static file copy
(`CodegenStaticOutput`) is not wired yet.

### Templates

Only `python` has bundled templates (default `classpath:`; sources under
[`templates/python/src/db/`](../templates/python/src/db/) and
[`templates/python/src/http/`](../templates/python/src/http/) with `server/`,
`client/`, and `models/` subtrees). Other languages require an explicit
`templateDirectory` whose classpath contains every required template.

Bundled codegen templates are inferred from enabled dialects/frameworks — do
not require per-artifact `artifacts` entries in `smithplates`.

Plugin config validation checks top-level templates required by enabled
dialects/frameworks.
