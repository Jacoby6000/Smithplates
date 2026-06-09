# Validate target path parsing and Python service-type discovery.
# Requires ROOT (repository root) in the caller.

smithystache_validate_target_usage() {
  cat <<'EOF' >&2
Targets (--target):
  all                 full repo (default)
  plugin              Scala plugin modules only
  python              all Python service types (discovered)
  python/db           db service type only
  python/db/sqlite    db sqlite dialect + shared db files
  python/db/postgres  db postgres dialect + shared db files
EOF
}

smithystache_validate_normalize_target() {
  local target="${1:-all}"
  target="$(printf '%s' "${target}" | tr '[:upper:]' '[:lower:]')"
  case "${target}" in
    all|"") echo all ;;
    plugin) echo plugin ;;
    python) echo python ;;
    python/db) echo python/db ;;
    python/db/sqlite) echo python/db/sqlite ;;
    python/db/postgres) echo python/db/postgres ;;
    *)
      echo "error: unknown validate target: ${target}" >&2
      smithystache_validate_target_usage
      return 2
      ;;
  esac
}

smithystache_validate_target_is_python() {
  case "${1:-all}" in
    python|python/db|python/db/sqlite|python/db/postgres) return 0 ;;
    *) return 1 ;;
  esac
}

smithystache_validate_target_needs_postgres_docker() {
  case "${1:-all}" in
    all|python|python/db|python/db/postgres) return 0 ;;
    *) return 1 ;;
  esac
}

smithystache_validate_target_service_type() {
  local target="$1"
  case "${target}" in
    python/db|python/db/sqlite|python/db/postgres) echo db ;;
    python) echo all ;;
    *) echo "" ;;
  esac
}

smithystache_validate_target_impl() {
  local target="$1"
  case "${target}" in
    python/db/sqlite) echo sqlite ;;
    python/db/postgres) echo postgres ;;
    python|python/db) echo all ;;
    *) echo "" ;;
  esac
}

smithystache_validate_list_python_service_types() {
  local tests_root="${ROOT}/templates/python/tests"
  local -a service_types=()
  local case_dir service_type_dir service_type

  shopt -s nullglob
  for case_dir in "${tests_root}"/*/; do
    for service_type_dir in "${case_dir}expected/src"/*/; do
      service_type="$(basename "${service_type_dir}")"
      if [[ "${service_type}" != "expected" ]]; then
        local seen=0
        if [[ ${#service_types[@]} -gt 0 ]]; then
          local existing
          for existing in "${service_types[@]}"; do
            if [[ "${existing}" == "${service_type}" ]]; then
              seen=1
              break
            fi
          done
        fi
        if [[ ${seen} -eq 0 ]]; then
          service_types+=("${service_type}")
        fi
      fi
    done
  done

  if [[ ${#service_types[@]} -eq 0 ]]; then
    return 0
  fi

  printf '%s\n' "${service_types[@]}" | sort -u
}

smithystache_validate_apply_python_target_env() {
  local target="$1"
  unset SMITHYSTACHE_PYTHON_SERVICE_TYPE SMITHYSTACHE_PYTHON_IMPL

  if ! smithystache_validate_target_is_python "${target}"; then
    return 0
  fi

  local service_type_filter
  service_type_filter="$(smithystache_validate_target_service_type "${target}")"
  local impl_filter
  impl_filter="$(smithystache_validate_target_impl "${target}")"

  if [[ -n "${service_type_filter}" && "${service_type_filter}" != "all" ]]; then
    export SMITHYSTACHE_PYTHON_SERVICE_TYPE="${service_type_filter}"
  fi
  if [[ -n "${impl_filter}" && "${impl_filter}" != "all" ]]; then
    export SMITHYSTACHE_PYTHON_IMPL="${impl_filter}"
  fi
}
