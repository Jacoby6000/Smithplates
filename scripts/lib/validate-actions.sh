# Run validate actions (lint, test) in the current environment.
# Invoked directly from a dev shell or once inside nix develop / Docker.

smithystache_validate_run_actions() {
  if [[ $# -eq 0 ]]; then
    echo "error: no validate actions to run" >&2
    return 2
  fi

  local action
  for action in "$@"; do
    case "${action}" in
      lint)
        ./scripts/run-linters.sh all
        ;;
      test)
        ./scripts/run-tests.sh all
        ;;
      *)
        echo "error: unknown validate action: ${action}" >&2
        return 2
        ;;
    esac
  done
}
