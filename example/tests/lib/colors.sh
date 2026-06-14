#!/usr/bin/env bash
# shellcheck shell=bash

if [[ -t 1 ]]; then
  COLOR_RESET=$'\033[0m'
  COLOR_GREEN=$'\033[32m'
  COLOR_RED=$'\033[31m'
  COLOR_YELLOW=$'\033[33m'
  COLOR_CYAN=$'\033[36m'
  COLOR_BOLD=$'\033[1m'
else
  COLOR_RESET=''
  COLOR_GREEN=''
  COLOR_RED=''
  COLOR_YELLOW=''
  COLOR_CYAN=''
  COLOR_BOLD=''
fi

print_pass() {
  printf '%bPASS%b %s\n' "${COLOR_GREEN}${COLOR_BOLD}" "${COLOR_RESET}" "$*"
}

print_fail() {
  printf '%bFAIL%b %s\n' "${COLOR_RED}${COLOR_BOLD}" "${COLOR_RESET}" "$*" >&2
}

print_skip() {
  printf '%bSKIP%b %s\n' "${COLOR_YELLOW}${COLOR_BOLD}" "${COLOR_RESET}" "$*"
}

print_info() {
  printf '%b==>%b %s\n' "${COLOR_CYAN}${COLOR_BOLD}" "${COLOR_RESET}" "$*"
}

print_summary() {
  local passed="$1"
  local failed="$2"
  local skipped="$3"
  printf '\n%bSummary%b  passed: %b%d%b  failed: %b%d%b  skipped: %b%d%b\n' \
    "${COLOR_BOLD}" "${COLOR_RESET}" \
    "${COLOR_GREEN}" "${passed}" "${COLOR_RESET}" \
    "${COLOR_RED}" "${failed}" "${COLOR_RESET}" \
    "${COLOR_YELLOW}" "${skipped}" "${COLOR_RESET}"
}
