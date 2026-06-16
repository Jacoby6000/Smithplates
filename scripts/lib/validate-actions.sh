# Run validate actions (lint, test) in the current environment.
# Invoked directly from a dev shell or once inside nix develop / Docker.

# shellcheck source=scripts/lib/validate-target.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/validate-target.sh"

smithystache_validate_run_lint_for_target() {
  local target="${SMITHYSTACHE_VALIDATE_TARGET:-all}"
  case "${target}" in
    all) ./scripts/run-linters.sh all ;;
    plugin) ./scripts/run-linters.sh scala ;;
    python|python/db|python/db/sqlite|python/db/postgres) ./scripts/run-linters.sh templates ;;
    examples|examples/python)
      ./scripts/run-example-linters.sh "$(smithystache_validate_example_project "${target}")"
      ;;
    *)
      echo "error: unknown validate target for lint: ${target}" >&2
      return 2
      ;;
  esac
}

smithystache_validate_run_test_for_target() {
  local target="${SMITHYSTACHE_VALIDATE_TARGET:-all}"
  case "${target}" in
    all) ./scripts/run-tests.sh all ;;
    plugin) ./scripts/run-tests.sh plugin ;;
    python|python/db|python/db/sqlite|python/db/postgres) ./scripts/run-tests.sh templates ;;
    examples|examples/python)
      ./scripts/run-example-tests.sh "$(smithystache_validate_example_project "${target}")"
      ;;
    *)
      echo "error: unknown validate target for test: ${target}" >&2
      return 2
      ;;
  esac
}

smithystache_validate_run_actions() {
  if [[ $# -eq 0 ]]; then
    echo "error: no validate actions to run" >&2
    return 2
  fi

  local action
  for action in "$@"; do
    case "${action}" in
      lint)
        smithystache_validate_run_lint_for_target
        ;;
      test)
        smithystache_validate_run_test_for_target
        ;;
      *)
        echo "error: unknown validate action: ${action}" >&2
        return 2
        ;;
    esac
  done
}
