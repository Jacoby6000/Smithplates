# Language templates

Bundled Scalate SSP templates and golden expected outputs for `@sqlService` codegen.

## Layout

```
language-templates/
  <language>/                 # e.g. python
    src/
      <feature>/              # e.g. db
        <feature-templates>   # models.ssp, service_protocol.ssp, …
        <implementation>/     # e.g. sqlite/, postgres/
          <impl-templates>
          tests/
        fragments/            # reusable SSP snippets for this feature
    expected-outputs/
      <test-case>/
        smithy/smithy-files.smithy
        src/<feature>/…       # golden generated src artifacts
        test/<feature>/…      # golden generated test artifacts
```

Bundled Python DB templates are packaged from `language-templates/python/src/db/` into the plugin JAR and loaded via default `classpath:` during `smithy build`. Golden tests compare rendered output against files under `language-templates/python/expected-outputs/`.

## Contributing

- Edit SSP sources under `language-templates/python/src/db/` (not under `modules/smithy-sql-service-renderer/src/main/resources/`).
- Refresh golden expectations under `language-templates/python/expected-outputs/<test-case>/` when intentional output changes.
- Run `sbtn smithySqlServiceRenderer/test` after template changes (golden render comparison).
- Run `./language-test-harnesses/python/run-tests.sh` to execute generated integration tests from expected-outputs.

See `language-templates/python/expected-outputs/README.md` for golden-test case conventions.
