#!/usr/bin/env bash
set -euo pipefail

main() {
  local root test
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  cd "$root"
  if ! command -v shellcheck >/dev/null; then
    printf '%s\n' 'Install ShellCheck 0.11.0 to run script checks; see README.md verification prerequisites.' >&2
    return 1
  fi
  shellcheck -x -P SCRIPTDIR jclaw scripts/*.sh scripts/tests/*.sh .githooks/pre-commit
  for test in scripts/tests/test_*.sh; do
    bash "$test"
  done
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
