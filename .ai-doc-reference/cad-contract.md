# Conceptual Architecture Document (CAD) Contract

The CAD is the shared interchange format for the architecture-design skill family:

- `design-target-architecture` emits a **target** CAD (from concept + architecture).
- `analyze-codebase-architecture` emits a **current** CAD (from existing code).
- `plan-architecture-refactor` consumes a current CAD and a target CAD and emits
  a refactoring plan.

Because all three speak the same schema, a current CAD and a target CAD are
directly comparable. Keep this schema stable; treat changes as breaking.

## Document structure

A CAD is a Markdown document with the sections below, in this order. Use the
exact headings so downstream skills can locate sections reliably.

```markdown
# Conceptual Architecture: <name>

- **Kind**: target | current
- **Source**: <concept+architecture brief | codebase path@commit>
- **Target language(s)**: <languages>

## Summary
<3-6 sentences: what the system does and the shape of the architecture.>

## Domain Model
For each type:
### <TypeName> (entity | value object)
- **Purpose**: <one sentence>
- **Fields**: `<name>: <type>` (one per line)
- **Invariants**: <rules always upheld>

## Interfaces
For each capability:
### <InterfaceName>
- **Responsibility**: <one sentence>
- **Methods**:
  - `<signature> -> <Result/Option<...>>` — <intent>
- **Depends on**: <other interfaces, or "none">

## Implementations
For each concrete type:
### <ImplName> implements <InterfaceName>
- **Dependencies (injected)**: <list, or "none">
- **Notes**: <persistence, external systems, side effects, where tracing goes>

## Test Doubles
- `<FakeName>` for `<InterfaceName>` — <in-memory/dummy behavior>

## Patterns & Conventions
<Optional. Named design patterns and conventions in use or intended,
e.g. "Repository per aggregate", "Strategy for pricing". Omit if none.>

## Relationships
- **Dependency direction**: <statement of which layers depend on which>
- **Diagram**: <Mermaid or ASCII dependency list>
- **Representative flow**: <how one key use case threads through interfaces>

## Open Questions
- <unresolved decisions that need user input — never silently defaulted>

## Risks & Trade-offs
- <decision> — <trade-off> — <rejected alternative and why>
```

## Field conventions

- **Signatures** use the chosen target language's syntax. If multiple languages
  apply, pick one canonical language and state it under *Target language(s)*.
- **Fallible operations** return a result/either type with a typed error.
  **Optional values** use an option/maybe type. Never use null or sentinels.
- **Identifiers** are stable and unique within the document so a refactoring
  plan can reference them precisely (e.g., interface `PaymentGateway`).
- **`Kind`** distinguishes a target (desired) CAD from a current (as-built) CAD;
  the refactor-planner relies on it.
- **Evidence (current CADs)**: when describing an as-built system, cite the
  source inline as `path/to/file.ext:Symbol` so claims are verifiable.
