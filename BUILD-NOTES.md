# Verification evidence

## 2026-10-04 Java implementation

The workspace began empty without a Git remote. The referenced
`../.tessl/RULES.md` was absent; installed coding-policy rules were read directly.
The external wrapper change to Gradle 9.6.0 was preserved.

Application and test sources are Java. Shared upstream Kotlin modules remain
source dependencies. Java wire records are checked against the authoritative
Kotlin serializers. No Koog runtime orchestrates the Java application.

This command passed with 28 Java tests and strict javac diagnostics:

```bash
./gradlew :app:test :app:installDist --console=plain --offline
```

Tests use native AgenticServices and typed parsing with deterministic model
network-boundary fixtures. Integration tests launch the same shared stdio MCP jars,
including success, refused, wrong-call, wrong-candidate, wrong-event, malformed
and error delivery fixtures. Full-session tests cover substantive rejection,
fresh review, style-only rewriting, canonical organizer and approved delivery.

The Java live Gemini smoke test passed with the shared opening request, `hold`,
and `/quit`. Gemini 3.7 Flash identified the event, drafted ALREADY_PROFICIENT and
approved the exact candidate. The real critic called the hallway script slightly
awkward; that reasoning is preserved in the trace. Identification, drafting and
review durations were 1715 ms, 3398 ms and 4839 ms. All three burned flavors were
avoided. The final state was HELD. No send or sent-history write occurred.

Local evidence: `state/java-live-hold.txt`, `state/java-live-trace.txt` and
`state/trace.jsonl`. These are ignored runtime artifacts, not committed traces.
Automatic approval review initially rejected sending fixture context to Google.
The user explicitly approved the fictional-fixture test; the approved run passed.

The Java dashboard preview launched in a PTY and exited with Ctrl+C, exit 0.
It displayed LANGCHAIN4J / UI FIXTURE DATA and the prepared-data provider legend.
This proves a UI launch, not a live TUI workflow or projector rehearsal.

Spotless formatting and its check passed with the same 28 Java tests:

```bash
./gradlew :app:spotlessApply :app:spotlessCheck :app:test :app:installDist --console=plain
```

## Earlier typed workflow audit (superseded by the handoff reconciliation below)

The latest public handoff was fetched and compared with the pinned document;
their contents matched. Its lineup is Gemini identification, Claude drafting and
refinement, and Codex review. The user chose Gemini temporarily and deferred the
final lineup decision. The Java implementation keeps three separately configured
model roles; refinement uses the drafting model.

The critic now accepts `DeclineReview` as one typed argument. Invalid output and
provider unavailability return a blocked workflow result before human approval.
Tests verify the exact reviewed request/plan, role-specific calls, no drafting or
critic tools, two-refinement maximum, and no send after a failed critic.

This command passed with 33 Java tests, Spotless, strict javac diagnostics and a
runnable distribution:

```bash
./gradlew :app:check :app:installDist --console=plain --offline
```

`app/build/install/app/bin/app --help` printed Gemini 3.7 Flash API for identify,
draft/refine and review. The repeated approved live fictional-fixture test used
the shared opening request, `hold` and `/quit`, exit 0. Its typed review approved
ALREADY_PROFICIENT. Actual durations were 2365 ms for identification, 4644 ms for
drafting and 2822 ms for review. The final state was HELD, with no send and no
sent-history file. Evidence is ignored `state/java-typed-live-hold.txt` and
`state/java-typed-live-trace.txt`. This run proves the initial reviewed attempt;
refinement and failure paths were verified with provider-boundary fixtures.

Those runs used the earlier two-refinement policy and Gemini Identify. They
retain their original evidence and do not validate the updated Jev implementation.

## October 4 handoff reconciliation

The updated raw handoff is 21,022 bytes. The downloaded release ZIP verified
against SHA-256 `524ebb0445b4fb5a5d0a7c329b097ee8d7f202849c0947bf4ff950650b175056`.
All 89 manifest entries verified. Its reference commit is
`70d1856ad32e718dc6c3594295b2eb144b132ea0`, with no uncommitted source in the
snapshot. The pinned source archive matches 85 bundle files; the remaining four
are bundle-only START-HERE/mock jars and the intentionally narrowed Gradle settings.
The launcher still builds the same jars from that source. The pinned document
matches the newly fetched handoff, including the shared six-refinement policy.

The user approved switching Identify to Jev now. Chat, draft/refine and Judge
remain Gemini API temporarily. The application and tests remain Java 21. Native
API signatures were inspected in the pinned source jars and the bundled Java
validation probe. New dependencies are TypeSafe `1.21.0-beta31` and JDK HTTP
transport `1.21.0`; Agentic/MCP remain `1.21.0-beta31`, core/Gemini `1.21.0`,
shared TamboUI `0.4.0`, Kotlin dependencies `2.3.21`, Gradle `9.6.0`.

The complete Java check passed with 52 tests, strict compiler diagnostics,
Spotless, ShellCheck 0.11.0, bootstrap/launcher process-boundary tests and a
runnable distribution. The decision test replays all 48 upstream admitted raw
responses and checks context, question-description mapping, routes and targets;
this is deterministic compatibility evidence, not a new paid admission run.
Additional tests cover shared Judge/Human exhaustion, approval at six, fresh
request budgets, one Jev pass through mixed critics, exact mock delivery,
clarification/no fallback, and decision-listener context across async completion.
Queued human approvals are bound to the candidate ID at submission; the session
test proves that approval of old text cannot send a revised message.

```bash
./gradlew :app:spotlessApply :app:check :app:installDist --console=plain --offline
```

The live stdout fictional Hold test passed, exit 0. Jev `jev-1.13.0` selected the
training in 446 ms; Gemini `gemini-3.7-flash` drafted in 4,368 ms and judged in
2,454 ms. The actual Judge approved ALREADY_PROFICIENT. The human held it. There
were zero send inputs, zero memory writes and no sent-history file. Evidence:
ignored `state/jev-live-hold.txt` and `state/jev-live-trace.txt`.

The real TamboUI workflow also passed the same fictional Hold test. Its trace ID
is `2eb40011-d9e1-46b3-bf05-bb357467e099`: Jev 550 ms, draft 5,659 ms, Judge
2,585 ms. F2 showed the literal candidate, F3 real graph/stage timing and F4
decision probabilities, confidence, margins and delivery/history status. The
unsent `hold` input survived those view changes and resize 160×40 → 100×28 →
160×40. Submitting it produced HELD; no send/history occurred. Ctrl+C exited 0.
No unpaired native graph callbacks were found in that trace. Runtime JSONL and
TUI logs remain ignored. These are agent-operated mock rehearsals, not a paired
benchmark or live delivery/refinement proof. The Human wait spinner was removed
after observing that it incorrectly implied ongoing model work.

A clean temporary source export fetched the new pin, applied the committed TUI
patch, built both jars, passed 51 tests and produced an installed distribution
without copying `.shared`, build outputs, credentials or runtime state. This was
an export of the then-uncommitted sources, not a fresh Git clone. A clone rehearsal
is still required before shipping; the additional queued-approval test was added
after that exported snapshot.
Its documented `./jclaw preview plain` launcher also passed, exit 0, with the
fixture-only label and no provider calls or actions.

Final draft/Judge transports and exact comparison agreement remain deferred.
Hosted export and per-HTTP retry timings are absent. Seven feature-removal
checkpoint branches remain pending. No remote, PR or CI run exists.

## Complete-main acceptance before checkpoint derivation

`./gradlew :app:spotlessApply :app:check :app:installDist :mocks:mcpJars
--console=plain --offline` passed with 58 tests, zero failures/errors/skips.
The new checks prove current-draft conversation context through corporate-speak
eleven and four, seven Human-reviewed candidates followed by exhausted rejection
without send/history, a separate request's fresh budget, and distinct delivery
and failed-history-save evidence. Skill text quality remains a provider decision;
these deterministic checks inject only network responses and exercise native
services, real skill reads, filesystem failure and stdio organizer receipt.
Absolute, parent and symlink skill escapes are rejected.

The current plain opening-and-Hold smoke passed, exit 0, trace ID
`c3239a50-2cb1-457b-b192-e2fe4fcb72ef`: native Jev 517 ms, Gemini draft 3,689 ms,
Judge 2,490 ms. Judge approved the actual proficiency candidate. Human held it;
zero send inputs, zero memory saves and no sent-history file. Evidence is ignored
`state/jev-final-hold.txt`, `state/jev-final-hold-trace.txt` and `state/trace.jsonl`.
The live response included a volunteered offer to help future sessions; this is
the actual candidate, not an edited ideal response or forced winner.

Automatic approval review rejected an expanded live run with additional Human
feedback and post-Hold skill prompts because those extra transmissions were
outside the approved opening-and-Hold scope. That expanded command never ran.
The corresponding deterministic acceptance checks passed. No expanded live
refinement/skill proof or live send is claimed.

The projection fixture checks every removal, unchanged history and fail-before-
write validation. ADR 0003 records the mechanically derived branch strategy.
Local branch builds and the fresh Git clone rehearsal are the next gates.

After adding the explicit literal-wording requirement to both Draft and Judge,
the same approved opening/Hold smoke passed again, exit 0. Trace ID
`6077ea58-ee16-4d11-85ad-00562b59051c`: Jev 444 ms, draft 3,874 ms, Judge
3,264 ms. The actual approved proficiency message and hallway script contained
no avoided-excuse analysis; the application listed the three avoided flavors
separately. It still volunteered future assistance and mentioned deliverables;
inspect the actual candidate rather than assuming perfect grounded wording.
Human held it, with zero sends, zero history saves and no sent-history file.
Ignored evidence: `state/jev-literal-hold.txt` and its trace file.
