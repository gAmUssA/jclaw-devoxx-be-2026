#!/usr/bin/env bash
set -euo pipefail

main() (
  local project_root fixture
  project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  fixture="$(mktemp -d "${TMPDIR:-/tmp}/jclaw-launcher-test.XXXXXX")"
  fixture="$(cd "$fixture" && pwd)"
  # ShellCheck does not resolve this subshell's EXIT-trap callback.
  # shellcheck disable=SC2329
  cleanup() {
    if ! rm -rf -- "$fixture"; then
      printf 'Could not clean test fixture %s; remove it explicitly.\n' "$fixture" >&2
    fi
    return 0
  }
  trap cleanup EXIT
  trap 'printf "FAIL launcher fixture at line %s\n" "$LINENO" >&2' ERR
  mkdir -p "$fixture/project/scripts" "$fixture/project/app/build/install/app/bin" "$fixture/caller"
  cp "$project_root/jclaw" "$fixture/project/jclaw"
  cat > "$fixture/project/.env" <<'ENV'
GOOGLE_API_KEY=file-fixture
TYPESAFE_API_KEY=file-jev-fixture
JCLAW_DECIDER=jev
JCLAW_GEMINI_MODEL=file-model
JCLAW_MOCK_DELIVERY=success
ENV
  cat > "$fixture/project/scripts/shared.sh" <<'SHARED'
#!/usr/bin/env bash
set -euo pipefail
main() { printf '%s\n' 'Prepared bootstrap boundary fixture'; }
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi
SHARED
  cat > "$fixture/project/gradlew" <<'GRADLE'
#!/usr/bin/env bash
set -euo pipefail
main() {
  printf '%s\n' "$*" > gradle-args
  printf '%s\n' 'Prepared build boundary fixture'
  if [[ "$JCLAW_TEST_BUILD_FAILURE" == 1 ]]; then return 7; fi
}
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi
GRADLE
  cat > "$fixture/project/app/build/install/app/bin/app" <<'APP'
#!/usr/bin/env bash
set -euo pipefail
main() {
  printf '%s\n' "$*" > app-args
  printf '%s\n' "$JCLAW_ROOT" > app-root
  printf '%s\n' "$GOOGLE_API_KEY" > fixture-key
  printf '%s\n' "$TYPESAFE_API_KEY" > fixture-jev-key
  printf '%s\n' "$JCLAW_DECIDER" > fixture-decider
  printf 'model=%s delivery=%s\n' "$JCLAW_GEMINI_MODEL" "$JCLAW_MOCK_DELIVERY"
}
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then main "$@"; fi
APP
  chmod +x "$fixture/project/gradlew" "$fixture/project/app/build/install/app/bin/app"
  # These fixed fixture values replace any inherited credentials before launch.
  export GOOGLE_API_KEY=shell-fixture JCLAW_MOCK_DELIVERY=wrong-call JCLAW_TEST_BUILD_FAILURE=0
  export TYPESAFE_API_KEY=shell-jev-fixture JCLAW_DECIDER=gemini
  unset JCLAW_GEMINI_MODEL
  cd "$fixture/caller"
  bash "$fixture/project/jclaw" guardrails plain > "$fixture/stdout" 2> "$fixture/stderr"
  [[ "$(cat "$fixture/project/fixture-key")" == shell-fixture ]]
  [[ "$(cat "$fixture/project/fixture-jev-key")" == shell-jev-fixture ]]
  [[ "$(cat "$fixture/project/fixture-decider")" == gemini ]]
  [[ "$(cat "$fixture/stdout")" == 'model=file-model delivery=wrong-call' ]]
  [[ "$(cat "$fixture/project/app-root")" == "$fixture/project" ]]
  [[ "$(cat "$fixture/project/app-args")" == 'guardrails plain' ]]
  [[ "$(cat "$fixture/project/gradle-args")" == '--console=plain :app:installDist :mocks:mcpJars' ]]

  unset GOOGLE_API_KEY JCLAW_MOCK_DELIVERY JCLAW_GEMINI_MODEL
  unset TYPESAFE_API_KEY JCLAW_DECIDER
  bash "$fixture/project/jclaw" preview > "$fixture/stdout" 2> "$fixture/stderr"
  [[ "$(cat "$fixture/project/fixture-key")" == file-fixture ]]
  [[ "$(cat "$fixture/project/fixture-jev-key")" == file-jev-fixture ]]
  [[ "$(cat "$fixture/project/fixture-decider")" == jev ]]
  [[ "$(cat "$fixture/stdout")" == 'model=file-model delivery=success' ]]
  rm "$fixture/project/app-args"
  export JCLAW_TEST_BUILD_FAILURE=1
  if bash "$fixture/project/jclaw" preview > "$fixture/stdout" 2> "$fixture/stderr"; then
    printf '%s\n' 'FAIL: launcher ignored a build failure' >&2
    return 1
  else
    [[ $? == 7 ]]
  fi
  [[ ! -e "$fixture/project/app-args" ]]
  printf '%s\n' 'PASS launcher: environment precedence, arguments, root, stdout separation and build-failure propagation'
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
