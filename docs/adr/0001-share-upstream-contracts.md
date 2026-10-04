# Share the upstream Devoxx contract

## Status
Accepted

## Date
2026-10-04

## Context
The comparison requires identical wire serializers, calendar fixtures, MCP jars,
prior-decline documents, corporate-speak skill and TamboUI dashboard. Maintaining
local copies would let the two implementations drift.

## Decision
Fetch the pinned Koog repository into the ignored `.shared/` dependency tree.
Build the domain and mocks unchanged. Parameterize shared TUI provider labels with
a committed narrow patch. Implement the application and tests in Java with
LangChain4j. Java wire records are verified against the authoritative Kotlin
serializers. The shared domain is a test dependency; its Koog annotations do not
execute the application workflow. Review the pin before rehearsal.

## Consequences
Both sides build the same mock source and serialize the same types. Initial setup
needs GitHub access. An upstream pin update must pass compatibility tests. The
shared TUI and domain build still resolve Koog dependencies.
