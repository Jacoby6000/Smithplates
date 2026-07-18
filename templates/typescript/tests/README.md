# TypeScript template golden tests

Golden cases live under `templates/typescript/tests/<case-name>/`. Each case runs `smithy build` via the same [`SmithyBuildTemplateRunner`](../../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/SmithyBuildTemplateRunner.scala) / [`CodegenTemplateTestSuite`](../../../modules/smithplates-plugin/src/test/scala/com/jacoby6000/smithplates/plugin/codegentest/CodegenTemplateTestSuite.scala) path as Python goldens. Fixture `smithy-build.json` files omit `maven.dependencies`; the runner loads the plugin from the sbt test classpath.

Bundled TypeScript templates are **HTTP client + models only** (axios or fetch). There is no TypeScript SQL or HTTP server golden suite today.

## Layout

```
templates/typescript/tests/<case-name>/
  smithy/smithy-files.smithy
  smithy-build.json                # smithplates.typescript.http.client; no maven block
  expected/
    src/generated/<smithy namespace>/client/*.ts
    src/generated/<smithy namespace>/clients/*.ts
    src/generated/<smithy namespace>/*.ts
    src/generated/smithplates/codegen/http/httpProblem.ts   # shared HttpProblem base when @httpProblem is used
```

## Cases

| Case | What it validates |
|------|-------------------|
| `http-fetch-combined-api` | Fetch client + models for a multi-route `@httpService` |
| `http-axios-combined-api` | Axios client variant of the same shape |
| `http-fetch-websocket-api` | `@websocket` client (`websocketClient.ts`) with fetch REST clients |

## Run golden render comparison

```bash
./scripts/run-template-golden-tests.sh
```

By default this runs the full `CodegenTemplateTestSuite` (Python **and** TypeScript
cases). The script's `--target` flag is Python harness-oriented for scoped runs;
for TypeScript-only golden iteration prefer:

```bash
sbtn "smithplatesPlugin/testOnly *CodegenTemplateTestSuite*"
```

TypeScript goldens are part of the shared suite (not `./validate --target typescript`). Example typecheck for generated consumer code: `./validate --target examples/typescript`.

## Refresh goldens

```bash
sbtn 'generateGoldenTemplatesFor typescript <case-name> [<case-name> ...]'
```

## Execution / typecheck

There is no `language-test-harnesses/typescript` golden harness yet. TypeScript typechecking runs against the petstore client under [`example/typescript/`](../../../example/typescript/) (`./scripts/run-example-linters.sh typescript`).
