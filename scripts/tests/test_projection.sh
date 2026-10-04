#!/usr/bin/env bash
set -euo pipefail

main() (
  local root fixture round project before session
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  # On a checkpoint this verifies the unchanged tool only when full input is present.
  if [[ -f "$root/.round-checkpoint" ]]; then
    printf '%s\n' 'SKIP projection fixture: checkpoint is derived input, not the complete baseline'
    return 0
  fi
  fixture="$(mktemp -d "${TMPDIR:-/tmp}/jclaw-projection-test.XXXXXX")"
  # shellcheck disable=SC2329
  cleanup() {
    if ! rm -rf -- "$fixture"; then printf 'Could not clean owned projection fixture %s.\n' "$fixture" >&2; fi
    return 0
  }
  trap cleanup EXIT
  trap 'printf "FAIL projection fixture at line %s\n" "$LINENO" >&2' ERR
  for round in {1..7}; do
    project="$fixture/$round"
    mkdir -p "$project/app/src/main/java/dev/gamov/jclaw" "$project/app/src/test/java/dev/gamov/jclaw" "$project/state"
    cp "$root/app/src/main/java/dev/gamov/jclaw/"{Main,DemoMode,DemoSession}.java "$project/app/src/main/java/dev/gamov/jclaw/"
    cp "$root/app/src/test/java/dev/gamov/jclaw/"{DemoSessionTest,JevSessionTest}.java "$project/app/src/test/java/dev/gamov/jclaw/"
    printf '%s\n' 'Existing confirmed history fixture' > "$project/state/sent-history.json"
    java "$root/app/src/main/java/dev/gamov/jclaw/RoundProjection.java" "$round" "$project" 0000000000000000000000000000000000000000
    [[ "$(cat "$project/state/sent-history.json")" == 'Existing confirmed history fixture' ]]
    session="$(cat "$project/app/src/main/java/dev/gamov/jclaw/DemoSession.java")"
    if ((round < 6)); then
      [[ "$session" != *'mcp.send(envelope)'* ]]
      [[ "$session" != *'workflow.humanVerdict(candidate'* ]]
      [[ ! -e "$project/app/src/test/java/dev/gamov/jclaw/JevSessionTest.java" ]]
    fi
    if ((round < 5)); then [[ "$session" != *'workflow.resume(run)'* ]]; fi
    if ((round < 4)); then [[ "$session" != *'tools.add(skills)'* ]]; fi
    if ((round < 3)); then [[ "$session" != *'tools.add(memories)'* ]]; fi
    if ((round < 2)); then [[ "$session" != *'tools.add(new McpTools.ReadOnly'* ]]; fi
  done
  # Invalid complete input fails before rewriting any consumer.
  project="$fixture/broken"
  cp -R "$fixture/7" "$project"
  printf '%s\n' '// checkpoint:end unexpected' >> "$project/app/src/main/java/dev/gamov/jclaw/Main.java"
  before="$(cat "$project/app/src/main/java/dev/gamov/jclaw/DemoMode.java")"
  if java "$root/app/src/main/java/dev/gamov/jclaw/RoundProjection.java" 1 "$project" 0000000000000000000000000000000000000000 > "$fixture/error.txt" 2>&1; then
    printf '%s\n' 'FAIL: malformed projection input was accepted' >&2
    return 1
  fi
  [[ "$(cat "$project/app/src/main/java/dev/gamov/jclaw/DemoMode.java")" == "$before" ]]
  printf '%s\n' 'PASS projection: seven removals, state preservation and malformed-input fail-before-write'
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
