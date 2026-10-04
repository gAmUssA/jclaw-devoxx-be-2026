# Native Jev decisions and a request-scoped Agentic review loop

## Status
Superseded by 0004

## Date
2026-10-04

## Context
The October 4 handoff moves intent/event selection to Jev and gives Judge and
Human one six-refinement budget. Restarting Identify after human feedback loses
the request identity and resets that budget. Generic chat listeners do not
instrument the released DecisionModel API.

## Decision
Use native `TypeSafeDecisionModel` from LangChain4j 1.21.0-beta31, JDK HTTP
transport 1.21.0, and the pinned upstream Jev questions/policy unchanged. Jev is
the default; Gemini Identify is an explicitly selected comparison mode with no
automatic fallback. Code assembles and rechecks canonical calendar identity.

Use native Agentic loop, sequence and conditional builders with typed draft and
Judge agents. Keep mutable retry state in one application request object. Human
verdicts enter a native HumanInTheLoop node, then resume the same request's
Refine/Judge path. UI input waiting is an application stage between invocations;
the native graph does not remain suspended during that wait. Delivery and final
history writes remain application capabilities outside the model agents.

The user chose Gemini API for chat, draft/refine and Judge temporarily. Claude
and Codex subscription transports remain deferred and are disclosed as differences.

## Consequences
The decision API and Agentic API are experimental and pinned. Shared resources
and recorded admission responses constrain compatibility tests. Jev credentials
are required for rounds 5–7; ordinary chat still uses Gemini. Decision listeners
record real typed IO, identity, usage and latency. API usage is not equivalent to
subscription cost; hosted export is not implemented.
