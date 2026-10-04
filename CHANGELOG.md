# Changelog

## Unreleased

- Clarify that Java reports avoided reasons separately and Judge reviews the
  typed candidate. Instruct Draft/Refine to remove rejected claims and avoid
  substantive excuse reuse, invented scheduling facts and unnecessary extra reasons.
- Preserve Gemini 3 function-call signatures with the native Google GenAI client
  while keeping thought summaries and opaque signatures out of traces (ADR 0005).
  Verify signed parallel and sequential calls through the actual MCP tools at a
  local HTTP boundary.
- Use Claude Opus 5.5 (`claude-opus-5-5`) for Draft/Refine and GPT-6 Astra
  (`gpt-6-astra`) for Judge through native LangChain4j API clients. Keep Jev
  Identify and Gemini chat/skills. Require the new API keys only for rounds 5–7,
  disclose actual transports in the UI and trace, and record the choice in ADR 0004.
- Verify native Anthropic/OpenAI HTTP requests, typed refinement, strict Judge
  output, provider failure blocking and shell-over-file credential precedence
  without contacting hosted providers.
- Derive seven feature-removal checkpoints from validated main using a Java 21
  source projection, with native capability tests and a staged-secret hook.
  Record the branch strategy in ADR 0003.
- Verify skill follow-up context at eleven then four, human exhaustion without
  delivery, a fresh request budget, and confirmed delivery with failed history save.

## 0.1.0 — 2026-10-04

- Reconcile the October 4 handoff and pin shared reference `70d1856`.
- Adopt native Jev 1.13.0 DecisionModel with the shared questions, distributions
  and confidence policy; retain Gemini Identify only as explicit comparison.
- Use native Agentic builders and one six-refinement request budget shared by
  Judge and Human; preserve identity and feedback without rerunning Identify.
- Add actual decision evidence, native Human verdict nodes, timed application
  stages, trace identity/timestamps, organizer reads and current TamboUI captions.
- Bind queued dashboard approval to the candidate ID present at submission,
  preventing approval of earlier text from authorizing a later refinement.
- Make bootstrap atomic, preserve shell-over-file configuration, and verify
  launcher/bootstrap behavior with ShellCheck and process-boundary fixtures.
- Record native decision and request-state boundaries in ADR 0002.

- Bootstrap the LangChain4j counterpart using pinned shared Devoxx contracts.
- Add bounded review, approval, receipt validation and confirmed-send persistence.
- Keep Gemini chat, drafting/refinement and review separately configurable
  pending the final lineup decision; native Jev handles Identify. Pass the typed `DeclineReview`
  to the critic and block invalid or unavailable review before human approval.
- Add scoped runtime skill discovery and contract tests.
- Record the shared-source dependency decision in ADR 0001.
