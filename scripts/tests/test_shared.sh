#!/usr/bin/env bash
set -euo pipefail

main() (
  local project_root fixture package archive commit case_root
  project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  fixture="$(mktemp -d "${TMPDIR:-/tmp}/jclaw-bootstrap-test.XXXXXX")"
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
  trap 'printf "FAIL bootstrap fixture at line %s\n" "$LINENO" >&2' ERR
  # shellcheck source=../../upstream.properties
  source "$project_root/upstream.properties"
  commit="$JCLAW_UPSTREAM_COMMIT"
  package="$fixture/package/jclaw-devoxx-$commit"
  mkdir -p "$package/gradle" "$package/domain/src/main/kotlin/jclaw/domain" \
    "$package/domain/src/main/resources/jclaw/jev" "$package/domain/src/main/resources/jclaw/workflow" \
    "$package/mocks" "$package/tui/src/main/kotlin/com/jbaruch/jclaw/tui" \
    "$package/skills/corporate-speak" "$package/memory/documents" "$fixture/bin"
  touch "$package/gradle/libs.versions.toml" "$package/domain/build.gradle.kts" \
    "$package/mocks/build.gradle.kts" "$package/tui/build.gradle.kts" \
    "$package/domain/src/main/kotlin/jclaw/domain/Model.kt" \
    "$package/domain/src/main/kotlin/jclaw/domain/Delivery.kt" \
    "$package/domain/src/main/resources/jclaw/jev/questions.json" \
    "$package/domain/src/main/resources/jclaw/jev/policy.json" \
    "$package/domain/src/main/resources/jclaw/workflow/policy.json" \
    "$package/skills/corporate-speak/SKILL.md" \
    "$package/memory/documents/2e72c8ef-dd58-524c-adb5-4e0c39e9a865" \
    "$package/memory/documents/2e3eea73-6e25-5ab8-8159-d986db9e0f38" \
    "$package/memory/documents/87ac1ec3-7e7f-56bd-a18f-3e731e57a676"
  # Build the minimal pre-patch text fixture from the committed diff.
  awk '
    /^@@ / { split($2, position, ","); target = -position[1]; while (line < target - 1) { print ""; line++ }; active = 1; next }
    /^--- / || /^\+\+\+ / { next }
    active && /^[ -]/ { print substr($0, 2); line++ }
  ' "$project_root/patches/shared-tui-providers.patch" > "$package/tui/src/main/kotlin/com/jbaruch/jclaw/tui/JclawTui.kt"
  archive="$fixture/source.tar.gz"
  tar -czf "$archive" -C "$fixture/package" "jclaw-devoxx-$commit"
  cat > "$fixture/bin/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
main() {
  local output="" argument
  printf 'fetch\n' >> "$JCLAW_TEST_FETCH_COUNT"
  if [[ "$JCLAW_TEST_FETCH_MODE" == fail ]]; then
    printf '%s\n' 'Prepared network failure fixture' >&2
    return 22
  fi
  while [[ $# -gt 0 ]]; do
    argument="$1"
    shift
    if [[ "$argument" == --output ]]; then
      output="$1"
      shift
    fi
  done
  if [[ "$JCLAW_TEST_FETCH_MODE" == malformed ]]; then
    printf '%s\n' 'Prepared malformed archive fixture' > "$output"
  else
    cp "$JCLAW_TEST_ARCHIVE" "$output"
  fi
}
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
CURL
  chmod +x "$fixture/bin/curl"
  export PATH="$fixture/bin:$PATH" JCLAW_TEST_ARCHIVE="$archive" JCLAW_TEST_FETCH_MODE=success

  new_case() {
    case_root="$fixture/$1"
    mkdir -p "$case_root/scripts" "$case_root/patches"
    cp "$project_root/scripts/shared.sh" "$case_root/scripts/shared.sh"
    cp "$project_root/upstream.properties" "$case_root/upstream.properties"
    cp "$project_root/patches/shared-tui-providers.patch" "$case_root/patches/shared-tui-providers.patch"
    export JCLAW_TEST_FETCH_COUNT="$case_root/fetch-count"
  }
  expect_failure() {
    local description="$1"
    if bash "$case_root/scripts/shared.sh" > "$case_root/output" 2>&1; then
      printf 'FAIL: %s unexpectedly succeeded\n' "$description" >&2
      return 1
    fi
    if compgen -G "$case_root/.shared-download.*" >/dev/null; then
      printf 'FAIL: %s left a staging directory\n' "$description" >&2
      return 1
    fi
  }

  new_case success
  bash "$case_root/scripts/shared.sh" > "$case_root/output" 2>&1
  [[ "$(cat "$case_root/.shared/COMMIT")" == "$commit" ]]
  [[ "$(cat "$case_root/.shared/UI_PATCH")" == provider-labels-v1 ]]
  bash "$case_root/scripts/shared.sh" >> "$case_root/output" 2>&1
  [[ "$(wc -l < "$case_root/fetch-count" | tr -d ' ')" == 1 ]]
  [[ "$(find "$case_root" -maxdepth 1 -name '.shared-download.*' | wc -l | tr -d ' ')" == 0 ]]

  new_case network-failure
  export JCLAW_TEST_FETCH_MODE=fail
  expect_failure 'Network failure'
  [[ ! -e "$case_root/.shared" ]]
  export JCLAW_TEST_FETCH_MODE=success
  bash "$case_root/scripts/shared.sh" > "$case_root/retry-output" 2>&1
  [[ -f "$case_root/.shared/COMMIT" ]]

  new_case malformed-archive
  export JCLAW_TEST_FETCH_MODE=malformed
  expect_failure 'Malformed archive'
  [[ ! -e "$case_root/.shared" ]]
  export JCLAW_TEST_FETCH_MODE=success

  new_case incomplete-cache
  mkdir "$case_root/.shared"
  printf '%s\n' "$commit" > "$case_root/.shared/COMMIT"
  expect_failure 'Incomplete cache'
  [[ ! -e "$case_root/fetch-count" ]]

  new_case wrong-pin
  mkdir "$case_root/.shared"
  printf '%s\n' 'a different pin' > "$case_root/.shared/COMMIT"
  expect_failure 'Wrong cached pin'
  [[ "$(cat "$case_root/.shared/COMMIT")" == 'a different pin' ]]

  new_case patch-failure
  printf '%s\n' 'unrelated source' > "$package/tui/src/main/kotlin/com/jbaruch/jclaw/tui/JclawTui.kt"
  tar -czf "$archive" -C "$fixture/package" "jclaw-devoxx-$commit"
  expect_failure 'Patch mismatch'
  [[ ! -e "$case_root/.shared" ]]

  case_root="$fixture/success"
  printf '%s\n' 'wrong patch marker' > "$case_root/.shared/UI_PATCH"
  expect_failure 'Wrong patch marker'
  printf '%s\n' provider-labels-v1 > "$case_root/.shared/UI_PATCH"
  printf '%s\n' 'unpatched or modified source' > "$case_root/.shared/tui/src/main/kotlin/com/jbaruch/jclaw/tui/JclawTui.kt"
  expect_failure 'False patch marker'
  printf '%s\n' 'PASS shared bootstrap: atomic install, cache reuse, failed-fetch recovery and cache validation'
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
