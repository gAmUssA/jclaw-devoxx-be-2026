#!/usr/bin/env bash
set -euo pipefail

fetch_shared() (
  local root commit archive staging="" source_dir
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  # The source pin is project configuration, not executable third-party content.
  # shellcheck source=../upstream.properties
  source "$root/upstream.properties"
  commit="$JCLAW_UPSTREAM_COMMIT"
  if [[ ! "$commit" =~ ^[0-9a-f]{40}$ ]]; then
    printf '%s\n' 'Invalid upstream commit; set a full lowercase commit hash in upstream.properties.' >&2
    return 1
  fi
  if [[ -f "$root/.shared/COMMIT" ]] && [[ "$(cat "$root/.shared/COMMIT")" == "$commit" ]]; then
    validate_shared "$root/.shared"
    patch_ui "$root/.shared" "$root"
    return 0
  fi
  if [[ -e "$root/.shared" ]]; then
    echo 'Shared source pin changed. Remove .shared explicitly before fetching the new pin.' >&2
    return 1
  fi
  staging="$(mktemp -d "$root/.shared-download.XXXXXX")"
  # ShellCheck does not resolve this subshell's EXIT-trap callback.
  # shellcheck disable=SC2329
  cleanup() {
    if ! rm -rf -- "$staging"; then
      printf 'Could not clean bootstrap staging directory %s; remove it explicitly.\n' "$staging" >&2
    fi
    return 0
  }
  trap cleanup EXIT
  archive="$staging/source.tar.gz"
  if ! curl --fail --location --connect-timeout 10 --max-time 120 "https://codeload.github.com/jbaruch/jclaw-devoxx/tar.gz/$commit" --output "$archive"; then
    printf '%s\n' 'Could not fetch shared source. Check GitHub connectivity and rerun bash scripts/shared.sh.' >&2
    return 1
  fi
  tar -xzf "$archive" -C "$staging"
  source_dir="$staging/jclaw-devoxx-$commit"
  validate_shared "$source_dir"
  patch_ui "$source_dir" "$root"
  printf '%s\n' "$commit" > "$source_dir/COMMIT"
  mv "$source_dir" "$root/.shared"
)

validate_shared() {
  local shared="$1" path
  for path in gradle/libs.versions.toml domain/build.gradle.kts mocks/build.gradle.kts tui/build.gradle.kts \
    domain/src/main/kotlin/jclaw/domain/Model.kt domain/src/main/kotlin/jclaw/domain/Delivery.kt \
    domain/src/main/resources/jclaw/jev/questions.json domain/src/main/resources/jclaw/jev/policy.json \
    domain/src/main/resources/jclaw/workflow/policy.json \
    tui/src/main/kotlin/com/jbaruch/jclaw/tui/JclawTui.kt skills/corporate-speak/SKILL.md \
    memory/documents/2e72c8ef-dd58-524c-adb5-4e0c39e9a865 \
    memory/documents/2e3eea73-6e25-5ab8-8159-d986db9e0f38 \
    memory/documents/87ac1ec3-7e7f-56bd-a18f-3e731e57a676; do
    if [[ ! -f "$shared/$path" ]]; then
      printf 'Incomplete shared source: missing %s. Remove .shared explicitly and rerun bash scripts/shared.sh.\n' "$path" >&2
      return 1
    fi
  done
}

patch_ui() {
  local shared="$1" root="$2"
  if [[ -f "$shared/UI_PATCH" ]]; then
    if [[ "$(cat "$shared/UI_PATCH")" != 'provider-labels-v1' ]]; then
      printf '%s\n' 'Unknown cached TUI patch; remove .shared explicitly and rerun bash scripts/shared.sh.' >&2
      return 1
    fi
    if ! patch --force --reverse --dry-run --silent -p1 -d "$shared" < "$root/patches/shared-tui-providers.patch"; then
      printf '%s\n' 'Cached TUI patch does not match its marker; remove .shared explicitly and rerun bash scripts/shared.sh.' >&2
      return 1
    fi
  else
    patch --batch --forward -p1 -d "$shared" < "$root/patches/shared-tui-providers.patch"
    printf '%s\n' 'provider-labels-v1' > "$shared/UI_PATCH"
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  fetch_shared
fi
