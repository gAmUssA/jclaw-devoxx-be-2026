# Sent history schema 1

`SentHistory` owns `state/sent-history.json`. It alone writes the envelope
`{"schema_version":1,"sends":[...]}`. Each send contains the literal event,
organizer, message, flavor, offset delivery timestamp, candidate ID and call ID.
Readers use `SentHistory.read()` and reject unsupported versions without writing.
Missing files mean no new confirmed sends. Shared prior-decline documents remain
separate. A matching validated receipt is required for every write. Atomic replace
protects against partial writes; run one application process per state directory.
Proposals and conversation are process-local. Reset deletes this file only.
