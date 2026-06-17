# Release

Smithplates publishes `com.jacoby6000:smithplates-plugin` as the consumer-facing coordinate. The plugin's transitive compile graph is also published so Maven resolves trait, renderer, and precompiled-template jars.

## Local publish

Use local Maven publishing when validating a consumer `smithy build` against local changes:

```bash
sbtn publishM2
```

Consumer `smithy-build.json` files should reference the version printed by:

```bash
sbtn print smithplatesPlugin/version
```

### Smithy CLI and `+` in dependency versions

`sbt-dynver` snapshot versions often contain `+` (for example `0.2.0+1-14e56739+20260616-2305-SNAPSHOT`). That string is valid for sbt and Maven, but the **Smithy CLI rejects any `+` in `maven.dependencies` version coordinates** before it resolves artifacts — it treats them as unsupported Gradle-style dynamic versions (`1.+`). You may see:

```text
'+' dependencies are not supported: com.jacoby6000:smithplates-plugin:jar:0.2.0+1-...
```

This is not Maven refusing to download; Smithy fails validation first. It also means Smithy will not resolve a dynver `+` snapshot from Maven Central even when that exact snapshot was published there.

For local example regeneration, always run `publishM2` before `smithy build` so `~/.m2` contains the plugin at the rendered version. [`./validate`](../../CONTRIBUTING.md#quick-validate) runs `publishM2` once at the start of each invocation; use [`scripts/run-example-build.sh`](../../scripts/run-example-build.sh) (or `./build-generated.sh` under `example/python/`), which shares the same once-per-session publish guard, when regenerating examples outside `./validate`. Do not run `smithy build` alone against a stale or hand-edited `smithy-build.json` without publishing first.

Golden template fixtures under `templates/python/tests/` omit `maven.dependencies` and load the plugin from the sbt test classpath instead.

## Maven Central

Releases are automated with `sbt-ci-release`.

- Stable releases are created from annotated tags beginning with `v`.
- Main-branch snapshots publish unique `-SNAPSHOT` versions derived from git history.
- Maintainers can trigger PR snapshots with the `!release` workflow when appropriate.

Only the plugin is the public coordinate users add manually. Renderer and IR modules are published because the plugin POM depends on them.

## Generated examples

The Python example stores rendered `smithy-build.json` files. When versions change or templates are updated, regenerate them with:

```bash
bash scripts/render-smithy-build.sh all
```

The render script filters `sbtn` output to avoid recording thin-client log lines as Maven versions.

## Secrets

Release workflows require Central Portal and PGP credentials configured in GitHub Actions. See the root [CONTRIBUTING.md](../../CONTRIBUTING.md) for the current secret names and operational details.
