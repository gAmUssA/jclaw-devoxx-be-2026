# Typed organizer evidence alongside shared workflow records

## Status
Accepted

## Date
2026-10-05

## Context
The application reads organizer sensitivity before planning, but originally
only displayed that result. Draft and Judge therefore lacked the evidence.
The six-field DeclineRequest and exact DeclineReview are authoritative shared
wire records; adding provider-specific facts to them would change that contract.
Putting the MCP result in userInstruction would blur its source.

## Decision
Keep the shared records unchanged. Pass a separate local OrganizerContext with
the canonical organizer name and bounded OrganizerSensitivity to both typed
generation interfaces through native AgenticScope state. Reject another organizer
or an invalid MCP value before generation. Retain this immutable context in the
request-scoped run across Judge and Human refinements.

The application supplies EASYGOING, NORMAL or TOUCHY from the MCP result. Standalone
workflow callers without MCP evidence use UNKNOWN explicitly; neither agent may
infer a sensitivity from it. Event/organizer binding, candidate approval and receipt
validation continue to use the shared records.

## Consequences
Organizer evidence can influence drafting and review without changing fixture,
delivery or history schemas. Both model prompts receive the same context on every
candidate. Tests verify real MCP TOUCHY evidence reaches all draft/review requests
through mixed refinements and rejects a mismatched organizer before model calls.
