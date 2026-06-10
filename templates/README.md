# Templates

Bundled Scalate SSP templates and golden expected outputs for `@sqlService` codegen.

## Layout

```
templates/
  <language>/                 # e.g. python
    src/
      <feature>/              # e.g. db
        <feature-templates>   # models.ssp, service_protocol.ssp, …
        <implementation>/     # e.g. sqlite/, postgres/
          <impl-templates>
          tests/
        fragments/            # reusable SSP snippets for this feature
    tests/
      <test-case>/
        smithy/smithy-files.smithy
        smithy-build.json
        expected/
          db/<dialect>.sql    # golden migration DDL (when dialect enabled)
          src/<feature>/…     # golden generated src artifacts
          test/<feature>/…    # golden generated test artifacts
```

Bundled Python DB templates are packaged from `templates/python/src/db/` into the plugin JAR and loaded via default `classpath:` during `smithy build`. Golden tests compare rendered output against files under `templates/python/tests/<test-case>/expected/`.

## Contributing

- Edit SSP sources under `templates/python/src/db/` (not under `modules/smithplates-sql-service-renderer/src/main/resources/`).
- Refresh golden expectations under `templates/python/tests/<test-case>/expected/` when intentional output changes.
- Run `./scripts/run-template-golden-tests.sh` after template changes (golden render comparison).
- Run `./language-test-harnesses/python/run-linters.sh` and `./language-test-harnesses/python/run-tests.sh` after template changes.

See [`templates/python/tests/README.md`](python/tests/README.md) for golden-test case conventions.
