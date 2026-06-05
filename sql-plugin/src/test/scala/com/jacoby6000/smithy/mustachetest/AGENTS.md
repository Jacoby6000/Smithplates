# Mustache template test framework

Test-only helpers under `com.jacoby6000.smithy.mustachetest` for resource-driven golden tests of Mustache-based codegen.

* Discover cases from `src/test/resources/mustache-template-tests/<behavior-name>/` (name the case for what it tests, e.g. `sql-json-structs-containing-unions`).
* Expected src outputs live under `<language>/src/<service-type>/...`; expected test outputs live under `<language>/test/<service-type>/<implementation>/` (for SQL service codegen: `python/src/db/...`, `python/test/db/sqlite/`, `python/test/db/postgres/`).
* Compare rendered output with exact expected files per [`MustacheTemplateVariant`](MustacheTemplateVariant.scala).
* Use `<variant-path>/unsupported.md` to skip a variant for a case (suppresses missing-expectations warnings).
* Failures report missing/unexpected files and contextual diffs via `TextContentDiff`.

Concrete backend example: [`SqlServiceCodegenPythonDbBackend`](../sql/codegen/SqlServiceCodegenPythonDbBackend.scala).
