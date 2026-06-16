#!/usr/bin/env bash
# shellcheck shell=bash
set -euo pipefail

TESTS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/colors.sh
source "${TESTS_ROOT}/lib/colors.sh"
# shellcheck source=lib/targets.sh
source "${TESTS_ROOT}/lib/targets.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") <client-language-target> <server-language-target> [test-case-name ...]

Run shared HTTP reference tests against a language-specific server using a
language-specific client adapter.

Examples:
  $(basename "$0") python python
  $(basename "$0") python python health-check pet-crud-lifecycle

Language targets live under ${TESTS_ROOT}/targets/<name>/ and must provide:
  start-server.sh  — writes server context JSON and prints base_url on stdout
  run-case.sh      — executes one *.case.json file using values from context
EOF
}

if [[ $# -lt 2 ]]; then
  usage >&2
  exit 2
fi

CLIENT_TARGET="$1"
SERVER_TARGET="$2"
shift 2

SERVER_TARGET_DIR="$(resolve_server_target_dir "${SERVER_TARGET}")"
CLIENT_TARGET_DIR="$(resolve_client_target_dir "${CLIENT_TARGET}")"

mapfile -t CASE_FILES < <(filter_case_files "$@")
if [[ ${#CASE_FILES[@]} -eq 0 ]]; then
  if [[ $# -gt 0 ]]; then
    print_fail "no test cases matched: $*"
    exit 1
  fi
  print_fail "no test cases found under ${TESTS_ROOT}/cases"
  exit 1
fi

RUN_DIR="$(mktemp -d "${TMPDIR:-/tmp}/smithystache-example-tests.XXXXXX")"
CONTEXT_FILE="${RUN_DIR}/server-context.json"
PID_FILE="${RUN_DIR}/server.pid"

stop_server() {
  if [[ ! -f "${PID_FILE}" ]]; then
    return 0
  fi
  local pid
  pid="$(<"${PID_FILE}")"
  if kill -0 "${pid}" 2>/dev/null; then
    kill "${pid}" 2>/dev/null || true
    wait "${pid}" 2>/dev/null || true
  fi
}

cleanup() {
  stop_server
  rm -rf "${RUN_DIR}"
}
trap cleanup EXIT

print_info "Starting ${SERVER_TARGET} server for shared HTTP tests"
BASE_URL="$("${SERVER_TARGET_DIR}/start-server.sh" "${CONTEXT_FILE}" "${PID_FILE}")"
print_info "Server ready at ${BASE_URL}"

passed=0
failed=0
skipped=0

for case_file in "${CASE_FILES[@]}"; do
  case_name="$(case_name_from_path "${case_file}")"
  print_info "Running ${case_name} (${CLIENT_TARGET} client -> ${SERVER_TARGET} server)"
  if "${CLIENT_TARGET_DIR}/run-case.sh" "${case_file}" "${CONTEXT_FILE}"; then
    print_pass "${case_name}"
    passed=$((passed + 1))
  else
    print_fail "${case_name}"
    failed=$((failed + 1))
  fi
done

print_summary "${passed}" "${failed}" "${skipped}"

if [[ "${failed}" -gt 0 ]]; then
  exit 1
fi
