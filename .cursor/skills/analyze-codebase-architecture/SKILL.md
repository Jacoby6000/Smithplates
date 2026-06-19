---
name: analyze-codebase-architecture
description: >-
  Produce an architectural analysis of an existing codebase — its design
  patterns, abstractions, concrete implementations, and how they relate — as a
  "current" Conceptual Architecture Document. Use when the user asks to analyze,
  map, reverse-engineer, or document the architecture of existing code, or wants
  a current-state architecture before planning a refactor.
---

# Analyze Codebase Architecture

Reverse-engineer an existing codebase into a **current** Conceptual Architecture
Document (CAD). Capture what *is*, with evidence — not what *should be*. Read the
shared schema at `.ai-doc-reference/cad-contract.md` (repo root) before emitting one;
producing a valid CAD with `Kind: current` is what lets
`plan-architecture-refactor` diff it against a target later.

Describe the system faithfully. Do not redesign, fix, or editorialize beyond the
**Risks & Trade-offs** section. Every structural claim must cite evidence as
`path/to/file.ext:Symbol`.

## Inputs required (do not guess)

- [ ] **Codebase scope**: which repo/paths are in scope (and any out of scope).
- [ ] **Commit/ref**: the state being analyzed (record it in `Source`).

If scope is unclear, ask. Where the code's *intent* is ambiguous, record it in
**Open Questions** rather than guessing.

## Workflow

Copy this checklist and track progress:

```
- [ ] 1. Confirm scope and commit
- [ ] 2. Survey structure (entry points, modules, layers, build files)
- [ ] 3. Catalog abstractions (interfaces/traits/protocols/abstract types)
- [ ] 4. Catalog implementations and how dependencies are wired
- [ ] 5. Identify design patterns and conventions
- [ ] 6. Map relationships (dependency direction, data flow, coupling)
- [ ] 7. Record risks/smells, then emit the current CAD
```

### 1. Confirm scope

Establish the paths and commit. Detect the primary language(s) and build system
from the manifests; state them rather than assuming.

### 2. Survey structure

Find entry points, top-level modules, and layer boundaries. Read manifests and
build files to understand dependencies and module graph. Prefer broad
exploration first (e.g. an `explore` subagent or glob/grep over the tree),
then drill into the parts that carry the architecture.

### 3. Catalog abstractions

List the key interfaces/traits/protocols/abstract classes and the core domain
types. For each, capture responsibility and signatures, citing the source. These
become the CAD's **Interfaces** and **Domain Model** sections.

### 4. Catalog implementations

For each abstraction, find the concrete implementations and record **how their
dependencies are wired**: constructor injection, DI containers, service
locators, global singletons, or ad hoc construction. Note side effects,
persistence, and external systems. Note any existing test doubles/fakes.

### 5. Identify patterns and conventions

Name the recurring design patterns (repository, strategy, factory, adapter,
etc.) and project conventions (error handling, module layout, naming). Record in
the CAD's **Patterns & Conventions** section.

### 6. Map relationships

Determine the actual **dependency direction** between layers/modules and whether
it points toward the domain. Capture a dependency diagram and a representative
data flow for one key use case. Note coupling hotspots and circular deps.

### 7. Record risks, then emit

Capture smells and risks (god objects, leaky abstractions, missing test seams,
hidden globals, inconsistent error handling) in **Risks & Trade-offs**. Then
render the CAD with `Kind: current`. Present it inline and offer to save it,
**asking where** rather than assuming a path.

## Analysis principles

- **Evidence over assertion**: every claim cites `path:Symbol`.
- **Describe, don't redesign**: improvements belong only in Risks & Trade-offs.
- **Faithful, not idealized**: if DI is via a container or globals, say so —
  don't depict it as clean injection.
- **Surface unknowns**: ambiguous intent goes to Open Questions.

## Quality checklist

- [ ] Conforms to the schema in `.ai-doc-reference/cad-contract.md`, with `Kind: current`.
- [ ] `Source` records the path(s) and commit/ref analyzed.
- [ ] Every interface and implementation cites evidence.
- [ ] Actual dependency wiring is described accurately (not idealized).
- [ ] Patterns & Conventions reflect what is actually used.
- [ ] No redesign outside Risks & Trade-offs; unknowns are Open Questions.
