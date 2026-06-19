---
name: design-target-architecture
description: >-
  Turn a high-level concept and a high-level architecture into a conceptual set
  of interfaces, domain models, implementations, and their relationships. Use
  when the user describes what a project should do plus a rough architecture and
  asks for the interfaces/classes/traits to implement, a target/goal
  architecture, an interface-first design, or a domain model sketch.
---

# Design Target Architecture

Produce a **conceptual architecture** — the interfaces, domain models, concrete
implementations, and relationships needed to realize a goal — from two inputs:

1. A high-level conceptual explanation of what the project should do.
2. A high-level architecture (layers, components, boundaries, constraints).

The output is a **Conceptual Architecture Document (CAD)**: the shared,
machine-and-human-readable contract used across this skill family. Read the full
schema at `.ai-doc-reference/cad-contract.md` (repo root) before emitting one. Producing a
valid CAD is what lets `analyze-codebase-architecture` and
`plan-architecture-refactor` consume this output later.

This is a *design* skill, not an implementation skill. Stay at the level of
signatures, responsibilities, and relationships. Do not write method bodies or
production logic.

## Inputs required (do not guess)

Before designing, confirm you have:

- [ ] **Concept**: the problem, primary use cases, and core behaviors.
- [ ] **Architecture**: intended layers/components, boundaries, and any hard
      constraints (runtime, persistence, external systems, performance).
- [ ] **Target language(s)**: the language for interface signatures.

If any input is missing or ambiguous, **ask the user** — do not invent
requirements or assume "sane" defaults. Missing decisions become entries in the
CAD's **Open Questions** section, not silent assumptions.

## Workflow

Copy this checklist and track progress:

```
- [ ] 1. Confirm inputs; list ambiguities to resolve
- [ ] 2. Extract the domain model (entities, value objects, invariants)
- [ ] 3. Define capabilities as interfaces (responsibility + signatures)
- [ ] 4. Specify implementations and hand-rolled test doubles
- [ ] 5. Map relationships (dependency direction, composition, data flow)
- [ ] 6. Capture open questions, risks, and trade-offs
- [ ] 7. Emit the CAD; offer to save it
```

### 1. Confirm inputs

Restate the concept and architecture in your own words in 3-5 bullets so the
user can correct misunderstandings early. Surface every ambiguity now. Ask
about anything that would otherwise force an assumption.

### 2. Extract the domain model

Identify the nouns and rules of the domain before any behavior:

- **Entities** (identity + lifecycle) and **value objects** (immutable, defined
  by attributes).
- **Invariants** each type must always uphold.
- Make illegal states unrepresentable where the type system allows it. Model
  optionality and errors explicitly rather than via nullable/loose fields.

Prefer thorough, precise domain models over thin data bags.

### 3. Define capabilities as interfaces

For each distinct capability, define an **interface** (trait/protocol/abstract
type) — program against interfaces, not concretions. For each interface record:

- **Responsibility**: one sentence; a single cohesive reason to exist.
- **Methods**: name + full signature including inputs and return types.
- **Errors as values**: fallible methods return a result/either type carrying a
  typed error — never signal failure by panicking, throwing for control flow,
  or returning sentinels. Model absence with an option type.
- **Dependencies**: which other interfaces it needs, injected explicitly.

Keep interfaces small and focused (favor segregation over god-interfaces).

### 4. Specify implementations and test doubles

For each interface list:

- The concrete implementation(s) that realize it, with their **explicit
  dependencies** (passed via constructor/parameters — no DI containers, no
  service locators, no global singletons).
- A **hand-rolled test double** (in-memory fake / dummy) usable in unit tests
  without a mocking framework.

### 5. Map relationships

Describe how the pieces fit:

- **Dependency direction** — must point inward toward the domain; outer layers
  depend on inner abstractions, never the reverse.
- **Composition / ownership** — which type holds or constructs which.
- **Data flow** — how a representative use case threads through the interfaces.

A compact diagram (Mermaid or an ASCII dependency list) is encouraged.

### 6. Capture open questions, risks, trade-offs

Every unresolved decision from step 1, plus design trade-offs you made and
alternatives you rejected (and why). This section is mandatory; an empty one
usually means assumptions were hidden.

### 7. Emit the CAD

Render the document using the schema in `.ai-doc-reference/cad-contract.md`. Present it
inline. Then offer to save it and **ask where** rather than assuming a path.

## Design principles to embody

These reflect the project's standards; the generated design must follow them:

- **Interface-first**: depend on abstractions; concretions are pluggable.
- **Functional core**: prefer pure functions, immutability, and explicit data
  transformation. Errors and absence are values (result/option), propagated
  with combinators — not exceptions or null.
- **No unsafe unwraps**: designs must make all cases handleable; document any
  case claimed unreachable.
- **Explicit dependencies**: constructor/parameter injection only.
- **DRY**: factor shared behavior into common interfaces/types.
- **Observability**: note where structured logging/tracing belongs (boundaries,
  branch points, errors) so implementers wire it in.
- **No invented requirements**: gaps go to Open Questions.

## Quality checklist

Before finishing, verify the CAD:

- [ ] Conforms to the schema in `.ai-doc-reference/cad-contract.md`.
- [ ] Every interface has a single, stated responsibility.
- [ ] Every fallible operation returns a typed error; absence uses an option.
- [ ] Every interface has at least one implementation and one test double.
- [ ] Dependencies are explicit and point toward the domain.
- [ ] No silent assumptions — gaps are in Open Questions.
- [ ] Stays conceptual (signatures and relationships, no method bodies).
