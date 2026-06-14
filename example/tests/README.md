# Shared reference HTTP tests

Language-neutral HTTP scenarios for Smithystache reference implementations. Each
case file describes one or more request/response steps that any language target
can execute against a running server.

## Running tests

From this directory:

```bash
./run-tests.sh <client-language-target> <server-language-target> [case-name ...]
```

Examples:

```bash
./run-tests.sh python python
./run-tests.sh python python health-check
```

The runner starts the server target once, then runs each selected case through
the client target. It prints a color-coded pass/fail summary when stdout is a
TTY.

## Layout

```
tests/
  run-tests.sh           # entry point
  cases/*.case.json      # shared scenarios
  schema/                # JSON Schema for case files
  lib/                   # shell helpers (colors, target resolution)
  targets/<language>/    # per-language server + client adapters
```

## Test case format (`smithystache.example.test-case/v1`)

Case files use the suffix `.case.json`. See
[`schema/test-case.v1.schema.json`](schema/test-case.v1.schema.json) for the
full schema.

### Top-level fields

| Field | Required | Description |
| --- | --- | --- |
| `schema` | yes | Must be `smithystache.example.test-case/v1` |
| `name` | yes | Stable case identifier (matches filename stem) |
| `description` | no | Human-readable summary |
| `variables` | no | Case-level variables; values may reference server context |
| `steps` | yes | Ordered HTTP interactions |

### Step fields

Each step has:

- `request` — `method`, `path`, optional `headers`, `json`, or raw `body`
- `expect` — required `status`; optional `headers`, `json`, or `body`
- `capture` — optional map of variable name → JSON Pointer (`$.field.nested`)

Steps run in order. Variables from `capture` and earlier steps are available
in later steps via `${variable_name}` substitution in strings anywhere under
`request` or `expect`.

### Server context

`start-server.sh` writes a JSON context file consumed by `run-case.sh`:

```json
{
  "base_url": "http://127.0.0.1:54321",
  "variables": {
    "seed_category_id": "…"
  }
}
```

Case files may bind server-provided values with `"${server.seed_category_id}"`
in `variables`.

### Flexible JSON expectations

Object values in `expect.json` support matchers:

| Matcher | Meaning |
| --- | --- |
| `{"$exists": true}` | Key must be present (any value) |
| `{"$type": "string"}` | Value must be a string |
| `{"$type": "integer"}` | Value must be an integer |
| `{"$type": "boolean"}` | Value must be a boolean |
| `{"$type": "string", "$minLength": 1}` | Non-empty string |

Unspecified response fields are ignored (partial object matching).

## Adding a language target

Create `targets/<language>/` with executable scripts:

### `start-server.sh <context-file> <pid-file>`

1. Boot the reference server on an ephemeral port with isolated state.
2. Write server context JSON (at minimum `base_url` and any `variables` cases
   need).
3. Write the server PID to `<pid-file>`.
4. Print `base_url` on stdout (last line is sufficient).

### `run-case.sh <case-file> <server-context-file>`

Parse the case file, perform HTTP requests against `base_url`, validate
responses, and exit non-zero on failure.

Optional `target.json` documents the target for tooling.

The Python target (`targets/python/`) uses `httpx` for HTTP and boots the
FastAPI server via `uvicorn`. Other languages should implement the same script
contract; they may use generated OpenAPI clients or raw HTTP libraries.

## Cases

| Case | Description |
| --- | --- |
| `health-check` | `GET /health` returns `{"status": "ok"}` |
| `pet-crud-lifecycle` | Create, read, update, delete a pet |

These mirror the pytest smoke tests in
[`../python/tests/test_api.py`](../python/tests/test_api.py).
