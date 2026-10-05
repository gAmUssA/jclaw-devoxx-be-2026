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
  cat > "$fixture/ModeExpectation.java" <<'JAVA'
import dev.gamov.jclaw.app.DemoMode;

public final class ModeExpectation {
  public static void main(String[] args) {
    if (DemoMode.defaultMode().round() != Integer.parseInt(args[0])) {
      throw new AssertionError("Checkpoint default does not match its round");
    }
  }
}
JAVA
  for round in {1..7}; do
    project="$fixture/$round"
    mkdir -p "$project/app/src/main/java/dev/gamov/jclaw/app" "$project/app/src/test/java/dev/gamov/jclaw/app" "$project/state"
    cp "$root/app/src/main/java/dev/gamov/jclaw/app/"{Main,DemoMode,DemoSession}.java "$project/app/src/main/java/dev/gamov/jclaw/app/"
    cp "$root/app/src/test/java/dev/gamov/jclaw/app/"{DemoSessionTest,JevSessionTest}.java "$project/app/src/test/java/dev/gamov/jclaw/app/"
    printf '%s\n' 'Existing confirmed history fixture' > "$project/state/sent-history.json"
    java "$root/app/src/main/java/dev/gamov/jclaw/checkpoint/RoundProjection.java" "$round" "$project" 0000000000000000000000000000000000000000
    javac -Xlint:all -Werror -d "$project/classes" "$project/app/src/main/java/dev/gamov/jclaw/app/DemoMode.java" "$fixture/ModeExpectation.java"
    java -cp "$project/classes" ModeExpectation "$round"
    [[ "$(cat "$project/state/sent-history.json")" == 'Existing confirmed history fixture' ]]
    session="$(cat "$project/app/src/main/java/dev/gamov/jclaw/app/DemoSession.java")"
    if ((round < 6)); then
      [[ "$session" != *'mcp.send(envelope)'* ]]
      [[ "$session" != *'workflow.humanVerdict(candidate'* ]]
      [[ ! -e "$project/app/src/test/java/dev/gamov/jclaw/app/JevSessionTest.java" ]]
    fi
    if ((round < 5)); then [[ "$session" != *'workflow.resume(run)'* ]]; fi
    if ((round < 4)); then [[ "$session" != *'tools.add(skills)'* ]]; fi
    if ((round < 3)); then [[ "$session" != *'tools.add(memories)'* ]]; fi
    if ((round < 2)); then [[ "$session" != *'tools.add(new McpTools.ReadOnly'* ]]; fi
  done
  # Invalid complete input fails before rewriting any consumer.
  project="$fixture/broken"
  cp -R "$fixture/7" "$project"
  cp "$root/app/src/main/java/dev/gamov/jclaw/app/DemoMode.java" "$project/app/src/main/java/dev/gamov/jclaw/app/DemoMode.java"
  printf '%s\n' '// checkpoint:end unexpected' >> "$project/app/src/main/java/dev/gamov/jclaw/app/Main.java"
  before="$(cat "$project/app/src/main/java/dev/gamov/jclaw/app/DemoMode.java")"
  if java "$root/app/src/main/java/dev/gamov/jclaw/checkpoint/RoundProjection.java" 1 "$project" 0000000000000000000000000000000000000000 > "$fixture/error.txt" 2>&1; then
    printf '%s\n' 'FAIL: malformed projection input was accepted' >&2
    return 1
  fi
  [[ "$(cat "$project/app/src/main/java/dev/gamov/jclaw/app/DemoMode.java")" == "$before" ]]
  printf '%s\n' 'PASS projection: seven round defaults, feature removals, state preservation and malformed-input fail-before-write'
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
