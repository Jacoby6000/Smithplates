# Backend selection and validate actions for validate / validate.ps1.
# Requires ROOT (repository root) and set -euo pipefail in the caller.

smithystache_validate_usage() {
  cat <<'EOF' >&2
usage: ./validate [lint,test|build|test|...]

Actions (comma-separated):
  build, lint   linters and compile (Scala + template harnesses)
  test          test suites (Scala + template harnesses)

Default: lint,test

Uses Nix when available (preferred), otherwise Docker. Override with SMITHYSTACHE_VALIDATE_BACKEND=nix|docker.
When multiple actions are requested, Nix/Docker is entered once for the full run.
EOF
}

smithystache_validate_in_nix_shell() {
  [[ -n "${IN_NIX_SHELL:-}" ]]
}

smithystache_validate_actions_need_docker_socket() {
  local action
  for action in "$@"; do
    if [[ "${action}" == "test" ]]; then
      return 0
    fi
  done
  return 1
}

smithystache_validate_detect_backend() {
  if [[ -n "${SMITHYSTACHE_VALIDATE_BACKEND:-}" ]]; then
    case "${SMITHYSTACHE_VALIDATE_BACKEND}" in
      nix|docker) echo "${SMITHYSTACHE_VALIDATE_BACKEND}"; return 0 ;;
      *)
        echo "error: SMITHYSTACHE_VALIDATE_BACKEND must be nix or docker" >&2
        return 2
        ;;
    esac
  fi

  if command -v nix >/dev/null 2>&1 && [[ -f "${ROOT}/flake.nix" ]]; then
    if nix flake metadata "${ROOT}" >/dev/null 2>&1; then
      echo nix
      return 0
    fi
  fi

  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    echo docker
    return 0
  fi

  echo none
}

smithystache_validate_run_actions_with_backend() {
  local backend="$1"
  shift

  case "${backend}" in
    nix)
      if smithystache_validate_in_nix_shell; then
        # shellcheck source=scripts/lib/validate-actions.sh
        source "${ROOT}/scripts/lib/validate-actions.sh"
        smithystache_validate_run_actions "$@"
      else
        nix develop "${ROOT}" --accept-flake-config --command ./scripts/run-validate-actions.sh "$@"
      fi
      ;;
    docker)
      # shellcheck source=scripts/lib/docker-image.sh
      source "${ROOT}/scripts/lib/docker-image.sh"
      smithystache_ensure_docker_image
      if smithystache_validate_actions_need_docker_socket "$@"; then
        smithystache_docker_run \
          -v /var/run/docker.sock:/var/run/docker.sock \
          -- \
          ./scripts/run-validate-actions.sh "$@"
      else
        smithystache_docker_run -- ./scripts/run-validate-actions.sh "$@"
      fi
      ;;
    *)
      echo "error: need Nix (flakes) or a running Docker daemon to validate" >&2
      echo "  Nix: https://nixos.org/download/" >&2
      echo "  Docker: https://docs.docker.com/get-docker/" >&2
      return 1
      ;;
  esac
}

smithystache_validate_normalize_action() {
  local action
  action="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  case "${action}" in
    build|lint) echo lint ;;
    test) echo test ;;
    *)
      echo "error: unknown validate action: $1" >&2
      smithystache_validate_usage
      return 2
      ;;
  esac
}

smithystache_validate_main() {
  local spec="${1:-lint,test}"
  local backend
  backend="$(smithystache_validate_detect_backend)" || return $?

  echo "==> validate (${spec}) via ${backend}"

  local -a seen=()
  local -a actions=()
  local part normalized

  IFS=',' read -r -a raw_parts <<< "${spec// /}"
  for part in "${raw_parts[@]}"; do
    [[ -z "${part}" ]] && continue
    normalized="$(smithystache_validate_normalize_action "${part}")" || return $?
    local already=0
    if [[ ${#seen[@]} -gt 0 ]]; then
      local seen_action
      for seen_action in "${seen[@]}"; do
        if [[ "${seen_action}" == "${normalized}" ]]; then
          already=1
          break
        fi
      done
    fi
    if [[ ${already} -eq 0 ]]; then
      seen+=("${normalized}")
      actions+=("${normalized}")
    fi
  done

  if [[ ${#actions[@]} -eq 0 ]]; then
    smithystache_validate_usage
    return 2
  fi

  smithystache_validate_run_actions_with_backend "${backend}" "${actions[@]}"
}
