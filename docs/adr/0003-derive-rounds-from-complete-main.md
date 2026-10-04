# Derive round branches from complete main

## Status
Accepted

## Date
2026-10-04

## Context
The handoff requires seven feature-removal branches after validating the complete
application. Independently implementing each round would let safety behavior,
shared fixtures and model wiring drift. A runtime flag alone would still expose
later commands on earlier checkpoints.

## Decision
Keep one complete Java implementation on main. Derive every checkpoint from the
same immutable commit using a Java 21 source projection and temporary worktrees.
Remove later DemoMode entries and marked application registrations/entry paths:
MCP, memory, skills, native decisions/workflow and Human/send. Retain common wire
types, helper implementations and local diagnostic evidence. Round 7 exposes the
complete inspection mode. The reduced enum prevents later commands from starting.

Run unchanged capability tests against each checkpoint's actual native services.
Tests for removed rounds are explicitly skipped; full human-session integration
test sources are removed below round 6. Shared wire/helper regression tests remain.
Keep confirmed history outside Git and never modify it during derivation or
checkout. Record the source commit and round in each branch's .round-checkpoint.

## Consequences
Checkpoint branches are generated products, not development branches. Fixes go
to main and require a new deliberate derivation. Projection validates its source
markers before writing and fails when the complete baseline has changed. Branches
share pinned dependencies and contain no alternative workflow implementation.
Each branch must pass compilation, formatting, script and native behavior checks.
