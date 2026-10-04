# Devoxx rehearsal

## Prepare

1. Agree exact model IDs and transports with the Koog side.
2. Run `bash scripts/shared.sh` and `./gradlew :app:check :app:installDist :mocks:mcpJars`.
3. Set local Jev and Gemini credentials using `.env.example`.
4. Open `./jclaw preview` at projector dimensions; confirm the fixture label.
5. Explicitly select the same memory snapshot on both sides. Use
   `rm -f state/sent-history.json` only when choosing the three-seed baseline;
   preserve confirmed sends after rehearsal, restart and branch changes.

Current lineup is Jev 1.13.0 native DecisionModel for Identify, Java request
assembly, and provisional Gemini 3.7 Flash API for chat, draft/refine and Judge.
Draft/Judge differ from Koog's Claude/Codex subscription CLIs, as agreed for now.
The dashboard provider legend shows actual configured role IDs. No final transport
agreement or comparable CLI cost claim is implied.

## Competitive rounds

Use chatbot, tools, memory, skills, workflow, guardrails and observability in order.
The shared opening request is in README.md. Calendar dates are fictional.
Round 4 demonstrates catalog metadata then scoped skill body loading. Corporate
language defaults to eleven and preserves facts, intent and commitments. A rewrite
sends nothing. The Koog framework-skill build meta-moment belongs to its own side.

Round 5 stops at a reviewed proposal. Show actual critic reasoning. Six refinements are permitted per request, shared by Judge and Human; a
rejection at six blocks. Approval at six remains possible. Round 6 owns the human loop:
inspect the candidate, reject it substantively, inspect the fresh review, then
approve or hold. Use the shared human feedback when it suits the candidate:

> Make the email shorter and more direct. Keep the proficiency reason; remove
> the Tuesday-afternoon reference.

Check the same run ID, one Identify pass, cumulative counter and fresh Judge
input. While Human waits, text is review feedback; `/chat ...` performs ordinary
chat without replacing the reviewed message. `/new ...` starts a separate request.
After Hold or delivery, “rewrite in corporate-speak” is ordinary conversation;
follow it with “tone it down to 4.” Neither rewrite sends or saves history.

Round 7 inspects `state/trace.jsonl` and the dashboard trace: actual route, request,
exact draft, every review, duration, human decision, receipt and memory write.
Jev decisions include actual probabilities/margins and raw typed IO. Calendar
and canonical assembly are application operations. Local native graph/API traces
and human verdict nodes are implemented; hosted export and subscription CLI traces are
absent. Prepared previews and test fixtures remain labelled.

## Receipt failure rehearsal

```bash
JCLAW_MOCK_DELIVERY=success ./jclaw guardrails plain
JCLAW_MOCK_DELIVERY=refused ./jclaw guardrails plain
JCLAW_MOCK_DELIVERY=wrong-call ./jclaw guardrails plain
JCLAW_MOCK_DELIVERY=wrong-candidate ./jclaw guardrails plain
JCLAW_MOCK_DELIVERY=wrong-event ./jclaw guardrails plain
JCLAW_MOCK_DELIVERY=malformed ./jclaw guardrails plain
JCLAW_MOCK_DELIVERY=error ./jclaw guardrails plain
```

Only a validated successful receipt writes history. Refusal is explicit; all other
failure fixtures remain unconfirmed. No automatic resend occurs. Restart to show
confirmed history persisting while conversation resets.

## Stop and reset

Ctrl+C stops the dashboard. `/quit` stops plain mode. `rm -f state/sent-history.json`
resets new sends and preserves the three committed upstream prior declines.
The Port closing workflow is outside competitive scoring; its deployment and
presentation narrative remain in the Koog/presentation workspaces.

## Reference and timing

Reference commit: `70d1856ad32e718dc6c3594295b2eb144b132ea0` (October 4 handoff).
Round budgets: 12 / 25 / 15 / 10 / 35 / 25 / 20 minutes. Koog starts odd rounds;
LangChain4j starts even rounds. Use `round/01-chatbot` through
`round/07-observability` with the matching command in README. All checkpoints
are projected from one validated complete main commit. Later modes are removed
from each checkpoint; branch switches preserve confirmed history.
Jev uses an 8-second timeout and two bounded transport retries. Native listener
latency includes those retries; per-HTTP retry timings are not exported. No silent
Gemini fallback occurs on a Jev contract or transport error.
