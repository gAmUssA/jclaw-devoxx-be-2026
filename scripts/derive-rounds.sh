#!/usr/bin/env bash
set -euo pipefail

main() (
  local root baseline temporary worktree round mode branch offline
  local -a modes gradle_args
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  cd "$root"
  offline="${1:-}"
  if [[ $# -gt 1 || ( -n "$offline" && "$offline" != --offline ) ]]; then
    printf '%s\n' 'Usage: bash scripts/derive-rounds.sh [--offline]' >&2
    return 2
  fi
  if [[ "$(git branch --show-current)" != main || -n "$(git status --porcelain)" ]]; then
    printf '%s\n' 'Derive from a clean, committed main; the script never stashes or changes the working branch.' >&2
    return 1
  fi
  baseline="$(git rev-parse --verify HEAD)"
  modes=(chatbot tools memory skills workflow guardrails observability)
  for round in {1..7}; do
    printf -v branch 'round/%02d-%s' "$round" "${modes[round-1]}"
    if git show-ref --verify --quiet "refs/heads/$branch"; then
      printf 'Branch already exists: %s. Preserve it or explicitly remove it before rederiving.\n' "$branch" >&2
      return 1
    fi
  done
  if ! command -v gitleaks >/dev/null || [[ "$(git config --get core.hooksPath)" != .githooks ]]; then
    printf '%s\n' 'Install gitleaks and configure this repo with git config core.hooksPath .githooks before committing checkpoints.' >&2
    return 1
  fi
  gradle_args=(--console=plain :app:check :app:installDist :mocks:mcpJars)
  if [[ -n "$offline" ]]; then gradle_args+=(--offline); fi
  # Required ordering: validate the complete build before removing features.
  bash scripts/shared.sh
  ./gradlew "${gradle_args[@]}"
  mkdir -p state/round-validation
  temporary="$(mktemp -d "${TMPDIR:-/tmp}/jclaw-rounds.XXXXXX")"
  worktree=''
  # shellcheck disable=SC2329
  cleanup() {
    if [[ -n "$worktree" && -d "$worktree" ]]; then
      if ! git -C "$root" worktree remove --force "$worktree"; then
        printf 'Could not remove owned temporary worktree %s; inspect it explicitly.\n' "$worktree" >&2
      fi
    fi
    if ! rmdir "$temporary"; then
      printf 'Temporary directory remains at %s.\n' "$temporary" >&2
    fi
    return 0
  }
  trap cleanup EXIT
  for round in {1..7}; do
    mode="${modes[round-1]}"
    printf -v branch 'round/%02d-%s' "$round" "$mode"
    worktree="$temporary/$mode"
    git worktree add -b "$branch" "$worktree" "$baseline"
    # Same pinned dependency snapshot, rebuilt jars, isolated application build/state.
    ln -s "$root/.shared" "$worktree/.shared"
    java app/src/main/java/dev/gamov/jclaw/RoundProjection.java "$round" "$worktree" "$baseline"
    (
      cd "$worktree"
      ./gradlew :app:spotlessApply "${gradle_args[@]}" > "$root/state/round-validation/$mode-build.txt" 2>&1
      app/build/install/app/bin/app --help > "$root/state/round-validation/$mode-help.txt"
      git add -- app/src .round-checkpoint
      git commit -m "Derive round $round: $mode"
    )
    git rev-parse "$branch" >> state/round-validation/commits.txt
    printf 'PASS %s (build, checks, native capability tests and launcher help)\n' "$branch"
    git worktree remove --force "$worktree"
    worktree=''
  done
)

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
