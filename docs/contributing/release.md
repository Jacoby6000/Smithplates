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

## Maven Central

Releases are automated with `sbt-ci-release`.

- Stable releases are created from annotated tags beginning with `v`.
- Main-branch snapshots publish unique `-SNAPSHOT` versions derived from git history.
- Maintainers can trigger PR snapshots with the `!release` workflow when appropriate.

Only the plugin is the public coordinate users add manually. Renderer and IR modules are published because the plugin POM depends on them.

## Generated examples

The Python example stores rendered `smithy-build.json` files. When versions change or templates are updated, regenerate them with:

```bash
bash example/python/render-smithy-build.sh
```

The render script filters `sbtn` output to avoid recording thin-client log lines as Maven versions.

## Secrets

Release workflows require Central Portal and PGP credentials configured in GitHub Actions. See the root [CONTRIBUTING.md](../../CONTRIBUTING.md) for the current secret names and operational details.
