# Java LangChain4j Devoxx counterpart

Java 21 implementation for **Codepocalypse Now: LangChain4j vs JetBrains Koog**.
All application and test sources are Java. The shared upstream domain, MCP mocks
and TamboUI module are Kotlin dependencies.

Jev `jev-1.13.0` decides intent/event. Gemini API handles chat and skills. Claude
Opus 5.5 API drafts/refines; GPT-6 Astra API judges the exact request and plan.
Live rounds 5–7 require TypeSafe, Google, Anthropic and OpenAI API credentials;
API calls may incur charges. This build uses API transports, while the Koog
reference uses Claude/Codex subscription CLIs. Provider-free tests and the
labelled UI preview require no credentials.

## Start and stop

Prerequisites: [JDK 21](https://adoptium.net/temurin/releases/?version=21),
[bash](https://www.gnu.org/software/bash/), [curl](https://curl.se/download.html),
[tar](https://www.gnu.org/software/tar/) and [patch](https://www.gnu.org/software/patch/).
Verified utility versions: bash 5.3.15, curl 8.7.1, bsdtar 3.5.3 and Apple patch
2.0-12u11. [ShellCheck 0.11.0](https://www.shellcheck.net/) is required
for `:app:check`. No machine-wide package installation is performed by this project.
The committed wrapper downloads Gradle 9.6.0. Initial setup needs internet access.
The launcher downloads the pinned shared source and builds both mock jars.

```bash
cp .env.example .env
# Set GOOGLE_API_KEY, TYPESAFE_API_KEY, ANTHROPIC_API_KEY and OPENAI_API_KEY in .env.
# JEV_API_KEY and GOOGLE_AI_API_KEY are accepted shell aliases.
./jclaw guardrails
# Stop the dashboard with Ctrl+C.
```

For stdout, use `./jclaw guardrails plain`; type `/quit` to stop.
In the dashboard input, enter `/copy` to copy the latest full assistant reply or
candidate message. `/copy reply` and `/copy candidate` select either explicitly.
These local commands copy the original text, preserving paragraphs and offscreen
content without panel borders. The candidate command copies only the literal email.
Copy confirmation appears in chat and does not replace the text available to copy.
Copying uses the JDK desktop clipboard; it requires a desktop session on the host
running the application. Clipboard availability errors appear in chat.
The dashboard needs an ANSI terminal at least 120 columns × 32 rows. Use `plain`
with `TERM=dumb` or redirected input/output. Automated PTY rehearsals must supply
`TERM=xterm-256color`; that setting is local to the command, not machine config.
Use your own [TypeSafe API key](https://docs.typesafe.ai/api) for Jev and
[Google AI Studio key](https://aistudio.google.com/apikey) for Gemini. Rounds 5–7
also need [Anthropic](https://platform.claude.com/settings/keys) and
[OpenAI](https://platform.openai.com/api-keys) keys. Rounds 1–4 need only Google.
The provider-free `./jclaw preview` and deterministic checks need no accounts.
The launcher preserves state and never switches branches. Existing shell settings
take precedence over `.env`, including `JCLAW_MOCK_DELIVERY` and model overrides.
Bootstrap installs `.shared` atomically; a failed fetch leaves no partial cache.
A changed pin requires explicitly replacing the old `.shared` source cache,
which is separate from `state/sent-history.json`.

Paste the exact shared opening request:

> Get me out of the Basic AI Proficiency Training on Tuesday, run by Dana from
> People Ops. Don't reuse an excuse I've already used on her - tell me which
> ones you're avoiding.

The fictional calendar training is Tuesday October 6, separate from the real
conference session schedule. The organizer server is a mock and contacts nobody.

## Rounds

| Command | Capability |
|---|---|
| `./jclaw chatbot` | Conversation with Gemini |
| `./jclaw tools` | Read-only calendar and organizer MCP tools |
| `./jclaw memory` | Conversation and literal durable history retrieval |
| `./jclaw skills` | Runtime skill catalog and scoped read-only skill loading |
| `./jclaw workflow` | Typed native agents and bounded review/refinement; no send gate |
| `./jclaw guardrails` | Human rejection, fresh review, approval and mock delivery |
| `./jclaw observability` | Same human workflow with actual model/tool/application traces |

Append `plain` to any command. `./jclaw preview` shows labelled prepared UI fixture
data; it makes no provider calls or sends and is not live framework evidence.
The full application stays on `main`. Feature-removal checkpoint branches have
the same ordered names: `round/01-chatbot`, `round/02-tools`, `round/03-memory`,
`round/04-skills`, `round/05-workflow`, `round/06-guardrails` and
`round/07-observability`. Each branch accepts its round and earlier commands;
later commands fail before provider initialization. The default is its highest
round, except that the complete build defaults to guardrails.

After validating and committing complete `main`, derive all seven with:

```bash
git config core.hooksPath .githooks
bash scripts/derive-rounds.sh
# Use --offline only after the pinned dependencies have been downloaded.
```

Derivation requires [gitleaks 8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1)
and Git (verified 2.55.0). It validates main, removes later mode entries and
application paths in temporary worktrees, checks each result, then commits it
locally. It refuses dirty main or existing checkpoint names. No branch is pushed.
Develop on main and deliberately rederive; do not edit seven implementations.
The [round projection decision](docs/adr/0003-derive-rounds-from-complete-main.md)
describes retained common code and explicit test exclusions. Build/help logs go
to ignored `state/round-validation/`. Branch checkout preserves ignored history;
the launcher rebuilds the selected branch's distribution before running it.

## Typed workflow and model roles

The [October 4 handoff](https://github.com/jbaruch/jclaw-devoxx/blob/70d1856ad32e718dc6c3594295b2eb144b132ea0/HANDOFF-LC4J.md)
assigns separate decision and generation roles. The user approved Jev Identify
and native API transports for Claude Draft/Refine and OpenAI Judge.

| Stage | Handoff provider | Current provider | Configuration |
|---|---|---|---|
| Intent + event decision | Jev API | Native `TypeSafeDecisionModel`, `jev-1.13.0` | `TYPESAFE_API_KEY`; `JCLAW_DECIDER=jev` |
| Canonical request assembly | Application code | Java, actual MCP/memory reads | Shared fixtures/policy |
| Ordinary chat and runtime skills | Gemini API | Gemini API, `gemini-3.7-flash` | `GOOGLE_API_KEY`; `JCLAW_CHAT_MODEL` |
| Draft and Refine `DeclineDeployment` | Claude / Anthropic, subscription CLI | Anthropic API, `claude-opus-5-5` | `ANTHROPIC_API_KEY`; `JCLAW_DRAFT_MODEL` |
| Judge exact `DeclineReview` → `DeclineCritique` | Codex / OpenAI, subscription CLI | OpenAI API, `gpt-6-astra` | `OPENAI_API_KEY`; `JCLAW_REVIEW_MODEL` |

Claude writes and refines the candidate. GPT-6 Astra evaluates the exact typed
request and candidate, returning a critique. These roles are separate from the
coding assistant building this repository. The Java app invokes native
`AnthropicChatModel` and `OpenAiChatModel`; it does not launch either CLI.
Call the Java critic an OpenAI API Judge, not Codex CLI. Compare API usage and
subscription usage separately; matching workflow roles does not establish
model, transport or cost equivalence with the Koog implementation.
The human is critic two; Java owns exact-candidate delivery and receipt validation.

`ModelProviders.java` fixes each transport to its workflow role. Set
`JCLAW_DRAFT_MODEL=claude-opus-5-5` and `JCLAW_REVIEW_MODEL=gpt-6-astra`; blank
overrides retain those defaults. The native OpenAI client requests strict JSON
Schema and disables response storage. Claude's adaptive thinking is not disabled;
only final answer text is returned and traced. No action tools are registered
with either generation agent. Missing credentials block startup; invalid or
unavailable output blocks the workflow without switching providers.

Gemini chat and optional comparison Identify default to
`JCLAW_GEMINI_MODEL=gemini-3.7-flash`; this setting cannot override Claude or
OpenAI models. `JCLAW_DECIDER=gemini` explicitly selects the comparison router using
`JCLAW_IDENTIFY_MODEL`; it is never a fallback for Jev failure. The launcher help,
dashboard and trace disclose actual configured models and transports.
See [ADR 0004](docs/adr/0004-native-claude-and-openai-api-roles.md) for the provider decision.

Jev receives the original user message, user/assistant conversation and actual
calendar records with date labels computed in Java. It gets no persona prompt or
action tools. Both questions use the shared descriptions, serialized as JSON text
where beta31 requires strings. Code validates model identity, answer types, choices,
distributions and the shared 0.60 confidence floors. Uncertain, absent or ambiguous
targets ask for clarification before Draft; CHAT ignores the speculative event.
Code rechecks the selected event and canonical organizer before assembling the
request, retrieves the three seed documents and confirmed history, and keeps
current-session proposed flavors separate from sent flavors.

Native LangChain4j Agentic loop, sequence and conditional builders orchestrate
typed draft and Judge agents. Judge receives one `DeclineReview` with the current
request and exact plan. Draft and Judge have no tools; a claimed supporting event
ID blocks. The shared workflow policy permits six refinements, seven candidates
per request. Judge and Human spend the same budget. A rejection at six blocks;
approval at six remains valid. Invalid or unavailable Judge blocks immediately.

Human rejection appends feedback to the instruction and resumes Refine → Judge →
Human with the same run, event, organizer and counter. Identify runs once. Human
verdicts are native HumanInTheLoop nodes; waiting for terminal input is an
application stage between native invocations. A separate request gets a fresh
budget. Round 5 stops at a reviewed proposal with no human prompt or send.

## Delivery and memory

Only a reviewed candidate reaches the application gate. `send` approves its exact
message; `hold` sends nothing. While the human gate is open, other text is feedback requiring Refine and fresh
Judge/human approval. `/chat ...` performs ordinary chat without replacing the
reviewed candidate; `/new ...` explicitly starts a separate request. After Hold
or delivery, style rewrites remain ordinary chat and cannot authorize new sends.
A human cannot override critic rejection. Dashboard approvals capture the
candidate ID at input submission; an approval queued for earlier text cannot
authorize a later revision.

The organizer receives eventId, organizerName, message, callId and candidateId.
Success requires a matching raw receipt and valid offset timestamp. Tool errors,
malformed results or mismatched IDs remain unconfirmed; check the organizer before
retrying. A matching explicit refusal is separate from uncertainty. Candidate
hashes bind content; they do not authenticate or guarantee idempotent delivery.

Only confirmed literal outbound messages become `state/sent-history.json`.
Restarting keeps that file and resets conversation. Shared prior-decline documents
remain separate from proposed alternatives. Reset new sent history with:

```bash
rm -f state/sent-history.json
```

## Verification and evidence

```bash
bash scripts/shared.sh
./gradlew :app:check :app:installDist :mocks:mcpJars --console=plain
./jclaw preview
```

Java compilation uses `-Xlint:all -Werror`. Tests exercise native LangChain4j output
parsing, shared Kotlin serializer compatibility, human decisions, every shared
MCP delivery fixture and restart persistence. Local HTTP tests run the native
Anthropic/OpenAI clients through typed drafting, refinement and review, checking
authentication, requested models, strict Judge schema and failure blocking.
Test model responses are network boundary fixtures; they are not live provider evidence.

Actual model inputs, outputs, usage, durations, review attempts and application
routes are written to ignored `state/trace.jsonl` with trace IDs, timestamps and
parent-stage context. Native DecisionModel listeners record Jev state/questions,
typed answers, requested/returned identity, usage and latency. The dashboard
decision view shows actual probabilities, confidence and margins. Calendar and
request assembly are application operations; Human verdicts are native graph
nodes, and send/history carry separate application evidence. MCP stderr appears in the tool
trace. No hosted telemetry export or subscription CLI transport is currently
implemented. API usage and any future CLI usage must be disclosed separately.

See [BUILD-NOTES.md](BUILD-NOTES.md) for measured verification and
[RUNBOOK.md](RUNBOOK.md) for rehearsal. The authoritative comparison contract is
[HANDOFF-LC4J.md](https://github.com/jbaruch/jclaw-devoxx/blob/70d1856ad32e718dc6c3594295b2eb144b132ea0/HANDOFF-LC4J.md).

The ignored `.shared/` dependency pins upstream commit `70d1856`. Mock source,
fixture documents and the corporate-speak skill are unchanged. The committed
TUI patch parameterizes provider labels and corrects coverage captions for the
actual native graph and application traces. The new upstream candidate limit is
seven. Skills, seeded documents, domain contracts and mock source are unchanged
from the selected upstream snapshot.
