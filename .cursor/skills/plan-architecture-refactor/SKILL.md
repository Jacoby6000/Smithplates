---
name: plan-architecture-refactor
description: >-
  Compare an existing architecture to a goal architecture and produce an
  incremental, test-guarded refactoring plan to get from one to the other. Use
  when the user has (or wants) a current and a target architecture and asks for
  a refactor plan, migration path, or step-by-step plan to evolve the codebase.
---

# Plan Architecture Refactor

Diff a **current** Conceptual Architecture Document (CAD) against a **target**
CAD and produce an ordered, incremental refactoring plan. Read the shared schema
at `.ai-doc-reference/cad-contract.md` (repo root) so you can align the two documents by their
stable identifiers.

The plan must be **incremental and behavior-preserving**: each step should leave
the codebase compiling and tests green. No big-bang rewrites.

## Inputs required (do not guess)

- [ ] **Current CAD** (`Kind: current`) — produce via `analyze-codebase-architecture`
      if missing.
- [ ] **Target CAD** (`Kind: target`) — produce via `design-target-architecture`
      if missing.

If either is absent, offer to generate it with the sibling skill before
planning. If the two CADs use different `Target language(s)` or are otherwise
incomparable, stop and ask the user. Never fabricate the missing side.

## Workflow

Copy this checklist and track progress:

```
- [ ] 1. Validate both CADs (kinds, comparability)
- [ ] 2. Gap analysis: align by identifier; classify each delta
- [ ] 3. Sequence deltas into safe, incremental steps
- [ ] 4. Choose a strategy per step (seams, adapters, migration)
- [ ] 5. Define validation and rollback per step
- [ ] 6. Emit the refactoring plan; offer to save
```

### 1. Validate inputs

Confirm one CAD is `current` and one is `target`, and that they are comparable.
Surface mismatches before proceeding.

### 2. Gap analysis

Align entries by their stable identifiers and classify every delta:

- **Domain model**: types to add / change / remove; invariant changes.
- **Interfaces**: added / removed / renamed / signature-changed.
- **Implementations**: to add, replace, or retire; rewiring of dependencies
  (e.g. container/global → explicit injection).
- **Relationships**: dependency-direction corrections, decoupling, layer moves.
- **Patterns**: patterns to introduce or remove.

Present this as a concise gap table before the step plan.

### 3. Sequence into incremental steps

Order the deltas so each step is independently shippable and dependency-aware:

- Prefer **behavior-preserving refactors first** (introduce seams), then
  behavior changes.
- Typical safe sequence per abstraction: add characterization tests → introduce
  the new interface → add an adapter/implementation → migrate callers
  incrementally (strangler) → delete the old path.
- Note which steps can proceed in parallel and which are blocked by others.

### 4. Strategy per step

For each step specify the technique: extract interface, introduce adapter,
branch-by-abstraction / strangler, parallel-change (expand–migrate–contract),
dependency inversion, etc. Favor explicit injection and functional-core seams so
units become testable with hand-rolled fakes (no mocking frameworks).

### 5. Validation and rollback

Each step states how it is verified (tests added/run, type-check, behavior
parity) and how to roll back if it fails. Add characterization tests *before*
changing behavior.

### 6. Emit the plan

Render using the template below. Present inline, then offer to save, **asking
where** rather than assuming a path.

## Output template

```markdown
# Refactoring Plan: <name>

- **From**: <current CAD source@commit>
- **To**: <target CAD source>

## Gap Summary
| Element | Current | Target | Delta |
|---------|---------|--------|-------|
| <id> | <state> | <state> | add/change/remove |

## Step Plan
### Step <n>: <title>
- **Goal**: <one sentence>
- **Touches**: <interfaces/types/files>
- **Technique**: <extract interface | adapter | strangler | parallel-change | ...>
- **Depends on**: <prior steps, or "none">
- **Behavior-preserving**: yes | no
- **Validation**: <tests/checks proving the step is safe>
- **Rollback**: <how to revert>

## Sequencing & Parallelism
<ordering, critical path, and which steps can run in parallel>

## Risks & Mitigations
- <risk> — <mitigation>

## Open Questions
- <unresolved items needing user input — never silently defaulted>
```

## Planning principles

- **Incremental & reversible**: small steps, each green; no big-bang.
- **Behavior-preserving by default**: separate refactors from behavior changes.
- **Test-guarded**: characterization tests precede risky changes.
- **Toward the domain**: rewiring moves dependencies inward, injection explicit.
- **No fabricated requirements**: gaps in the CADs become Open Questions.

## Quality checklist

- [ ] Every target/current delta is covered by a step (or an Open Question).
- [ ] Steps are ordered with explicit dependencies; parallelism noted.
- [ ] Each step is independently shippable with validation and rollback.
- [ ] Behavior-preserving steps are separated from behavior changes.
- [ ] No big-bang step; nothing leaves the build broken.
