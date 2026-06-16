# Backend selection and validate actions for validate / validate.ps1.
# Requires ROOT (repository root) and set -euo pipefail in the caller.

# shellcheck source=scripts/lib/validate-target.sh
source "${ROOT}/scripts/lib/validate-target.sh"

smithystache_validate_usage() {
  cat <<'EOF' >&2
usage: ./validate [lint,test|build|test|...] [--target TARGET]

Actions (comma-separated):
  build, lint   linters and compile (Scala + template harnesses)
  test          test suites (Scala + template harnesses)

Default: lint,test

Optional --target scopes lint/test to plugin, python, python/db, a dialect path, or example projects.
Uses Nix when available (preferred), otherwise Docker. Override with SMITHYSTACHE_VALIDATE_BACKEND=nix|docker.
When multiple actions are requested, Nix/Docker is entered once for the full run.
EOF
  smithystache_validate_target_usage
}

smithystache_validate_in_nix_shell() {
  [[ -n "${IN_NIX_SHELL:-}" ]]
}

smithystache_validate_actions_need_docker_socket() {
  local target="${SMITHYSTACHE_VALIDATE_TARGET:-all}"
  local action
  for action in "$@"; do
    if [[ "${action}" == "test" ]] && smithystache_validate_target_needs_postgres_docker "${target}"; then
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

smithystache_validate_docker_env_args() {
  local -a env_args=(
    -e "SMITHYSTACHE_VALIDATE_TARGET=${SMITHYSTACHE_VALIDATE_TARGET:-all}"
  )
  if [[ -n "${SMITHYSTACHE_PYTHON_SERVICE_TYPE:-}" ]]; then
    env_args+=(-e "SMITHYSTACHE_PYTHON_SERVICE_TYPE=${SMITHYSTACHE_PYTHON_SERVICE_TYPE}")
  fi
  if [[ -n "${SMITHYSTACHE_PYTHON_IMPL:-}" ]]; then
    env_args+=(-e "SMITHYSTACHE_PYTHON_IMPL=${SMITHYSTACHE_PYTHON_IMPL}")
  fi
  printf '%s\0' "${env_args[@]}"
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
        nix develop "${ROOT}" --accept-flake-config --command env \
          SMITHYSTACHE_VALIDATE_TARGET="${SMITHYSTACHE_VALIDATE_TARGET:-all}" \
          ${SMITHYSTACHE_PYTHON_SERVICE_TYPE:+SMITHYSTACHE_PYTHON_SERVICE_TYPE="${SMITHYSTACHE_PYTHON_SERVICE_TYPE}"} \
          ${SMITHYSTACHE_PYTHON_IMPL:+SMITHYSTACHE_PYTHON_IMPL="${SMITHYSTACHE_PYTHON_IMPL}"} \
          ./scripts/run-validate-actions.sh "$@"
      fi
      ;;
    docker)
      # shellcheck source=scripts/lib/docker-image.sh
      source "${ROOT}/scripts/lib/docker-image.sh"
      smithystache_ensure_docker_image
      local -a docker_env_args=()
      docker_env_args+=(-e "SMITHYSTACHE_VALIDATE_TARGET=${SMITHYSTACHE_VALIDATE_TARGET:-all}")
      if [[ -n "${SMITHYSTACHE_PYTHON_SERVICE_TYPE:-}" ]]; then
        docker_env_args+=(-e "SMITHYSTACHE_PYTHON_SERVICE_TYPE=${SMITHYSTACHE_PYTHON_SERVICE_TYPE}")
      fi
      if [[ -n "${SMITHYSTACHE_PYTHON_IMPL:-}" ]]; then
        docker_env_args+=(-e "SMITHYSTACHE_PYTHON_IMPL=${SMITHYSTACHE_PYTHON_IMPL}")
      fi
      if smithystache_validate_actions_need_docker_socket "$@"; then
        smithystache_docker_run \
          "${docker_env_args[@]}" \
          -v /var/run/docker.sock:/var/run/docker.sock \
          -- \
          ./scripts/run-validate-actions.sh "$@"
      else
        smithystache_docker_run \
          "${docker_env_args[@]}" \
          -- \
          ./scripts/run-validate-actions.sh "$@"
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

smithystache_validate_parse_args() {
  VALIDATE_ACTION_SPEC="lint,test"
  local target="all"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --target)
        if [[ $# -lt 2 ]]; then
          echo "error: --target requires a value" >&2
          smithystache_validate_usage
          return 2
        fi
        target="$2"
        shift 2
        ;;
      --target=*)
        target="${1#--target=}"
        shift
        ;;
      *)
        VALIDATE_ACTION_SPEC="$1"
        shift
        ;;
    esac
  done

  target="$(smithystache_validate_normalize_target "${target}")" || return $?
  export SMITHYSTACHE_VALIDATE_TARGET="${target}"
  smithystache_validate_apply_python_target_env "${target}"
}

smithystache_validate_main() {
  smithystache_validate_parse_args "$@" || return $?
  local spec="${VALIDATE_ACTION_SPEC}"

  local backend
  backend="$(smithystache_validate_detect_backend)" || return $?

  local target="${SMITHYSTACHE_VALIDATE_TARGET:-all}"
  echo "==> validate (${spec}, target=${target}) via ${backend}"

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
