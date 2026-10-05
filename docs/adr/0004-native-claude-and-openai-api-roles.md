# Native Claude drafting and OpenAI review over APIs

## Status
Accepted

## Date
2026-10-04

## Context
ADR 0002 temporarily used Gemini for every generation role. The user now requires
Claude Draft/Refine and an OpenAI Judge, authorizes API transports when subscription
CLI integration is unavailable, and selects Opus 5.5 and Astra. The Koog reference
uses subscription CLIs; LangChain4j supplies native API clients. A custom CLI shim
or hand-written HTTP client would add process, authentication and parsing behavior.

## Decision
Supersede only the generation-provider choice in ADR 0002. Use LangChain4j
`langchain4j-anthropic:1.21.0` for `claude-opus-5-5` Draft/Refine and
`langchain4j-open-ai:1.21.0` for `gpt-6-astra` Judge. Keep Gemini for chat/skills,
Jev for Identify, and Java for canonical assembly, approval, delivery and history.

Require Anthropic and OpenAI credentials only when rounds 5–7 start. Role-specific
model settings override their own defaults; the Gemini setting cannot change
providers. Missing credentials or invalid/unavailable model output block; no
cross-provider fallback is permitted. Draft and Judge remain tool-free typed
native agents. OpenAI uses strict JSON Schema and `store=false`. Claude returns
the final answer without thinking text; do not disable Opus 5.5 adaptive thinking.
Trace final outputs and actual provider/model identities without credentials or
private thinking. The existing shared refinement budget and approval rules hold.

## Consequences
Live workflows need four API accounts/credentials; rounds 1–4 need only Gemini.
Provider-free preview and local HTTP boundary tests remain available. API usage
and costs differ from subscription CLI usage. Call the Java Judge an OpenAI API
model, not Codex CLI. Paired comparison must disclose the transport/model difference.
Native clients own authentication and HTTP parsing; fixture tests verify both
provider requests and typed review/refinement behavior.
