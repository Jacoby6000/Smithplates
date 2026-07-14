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
  examples            all example reference projects (regenerate, lint Smithplates-owned example code, test)
  examples/python     Python petstore reference (regenerate, ruff, mypy, pytest, HTTP tests)
  examples/typescript TypeScript petstore reference (regenerate, tsc, tests)
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
    examples) echo examples ;;
    examples/python) echo examples/python ;;
    examples/typescript) echo examples/typescript ;;
    *)
      echo "error: unknown validate target: ${target}" >&2
      smithystache_validate_target_usage
      return 2
      ;;
  esac
}

smithystache_validate_target_is_examples() {
  case "${1:-all}" in
    examples|examples/python|examples/typescript) return 0 ;;
    *) return 1 ;;
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
    all|plugin|python|python/db|python/db/postgres|examples|examples/python|examples/typescript) return 0 ;;
    *) return 1 ;;
  esac
}

smithystache_validate_example_project() {
  local target="$1"
  case "${target}" in
    examples/python) echo python ;;
    examples/typescript) echo typescript ;;
    examples) echo all ;;
    *) echo "" ;;
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
  local -a namespaces=()
  local case_dir generated_root ns_dir ns

  shopt -s nullglob
  for case_dir in "${tests_root}"/*/; do
    generated_root="${case_dir}expected/src/generated"
    if [[ ! -d "${generated_root}" ]]; then
      continue
    fi
    for ns_dir in "${generated_root}"/*/; do
      ns="$(basename "${ns_dir}")"
      local seen=0
      if [[ ${#namespaces[@]} -gt 0 ]]; then
        local existing
        for existing in "${namespaces[@]}"; do
          if [[ "${existing}" == "${ns}" ]]; then
            seen=1
            break
          fi
        done
      fi
      if [[ ${seen} -eq 0 ]]; then
        namespaces+=("${ns}")
      fi
    done
  done

  if [[ ${#namespaces[@]} -eq 0 ]]; then
    return 0
  fi

  printf '%s\n' "${namespaces[@]}" | sort -u
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
