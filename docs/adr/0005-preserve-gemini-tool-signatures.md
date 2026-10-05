# Preserve Gemini tool signatures through the native GenAI integration

## Status
Accepted

## Date
2026-10-04

## Context
The tools rehearsal executed both MCP reads, then Gemini rejected the follow-up.
The old Google AI mapper retains and resends signatures only when thinking is
returned and sent. Gemini 3 requires the original signature on function-call
parts. Enabling visible thinking couples protocol correctness to private output.
A hand-written JDK HTTP client would make the app own Google's conversation wire
format and signature association.

## Decision
Use `langchain4j-google-genai:1.21.0-beta31` and `GoogleGenAiChatModel` for all
Gemini roles. Preserve per-call signatures through native message attributes,
independently of returning or sending thought summaries; disable both summaries.
Keep model IDs, Gemini API authentication, timeout, zero application retries and
all role assignments in ADR 0004 unchanged. Pin the directly configured Google
SDK at `1.71.0`; set its HTTP retry attempts to one independently of LangChain4j
retries. Sanitize AI messages for trace display
without changing native history. Test the real client and MCP tool loop against
a local HTTP provider fixture with parallel and sequential signed function calls.

## Consequences
The Google integration is experimental and pinned alongside Agentic and TypeSafe.
The Google SDK owns the wire format; the app retains ordinary input/output
evidence without logging signatures or private thinking. Every checkpoint and
workshop snippet uses the same client. Live provider access remains a separate
rehearsal check.
