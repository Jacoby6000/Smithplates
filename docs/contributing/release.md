# Release

Smithplates publishes `com.jacoby6000:smithplates-plugin` as the consumer-facing coordinate. The plugin's transitive compile graph is also published so Maven resolves trait, renderer, and precompiled-template jars.

Release history and migration notes live in [`CHANGELOG.md`](../../CHANGELOG.md). Update that file in the same PR as user-visible behavior changes.

## Local publish

Use local Maven publishing when validating a consumer `smithy build` against local changes:

```bash
sbtn publishM2
```

Consumer `smithy-build.json` files should reference the version printed by:

```bash
sbtn print smithplatesPlugin/version
```

Build and publish require **JDK 17**.

## Maven Central

Releases are automated with `sbt-ci-release`.

- Stable releases are created from annotated tags beginning with `v`.
- Main-branch snapshots publish unique `-SNAPSHOT` versions derived from git history.
- Maintainers can trigger PR snapshots with the `!release` workflow when appropriate.

Only the plugin is the public coordinate users add manually. Renderer and IR modules are published because the plugin POM depends on them.

After tagging a stable release, ensure [`CHANGELOG.md`](../../CHANGELOG.md) has a dated section for that version (move items out of `Unreleased`) and that the compare links at the bottom of the file point at the new tags.

## Generated examples

The Python and TypeScript examples store rendered `smithy-build.json` files. When versions change or templates are updated, regenerate them with:

```bash
bash scripts/render-smithy-build.sh all
```

The render script filters `sbtn` output to avoid recording thin-client log lines as Maven versions. End-to-end example regeneration: `./scripts/run-example-build.sh all`.

## Secrets

Release workflows require Central Portal and PGP credentials configured in GitHub Actions. See [Getting started — GitHub Actions secrets](getting-started.md#github-actions-secrets) for the current secret names and operational details.
