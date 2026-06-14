#!/usr/bin/env python3
"""Execute a language-neutral HTTP test case against a running reference server."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import httpx

VARIABLE_PATTERN = re.compile(r"\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}")
SERVER_VARIABLE_PATTERN = re.compile(r"^\$\{server\.([a-zA-Z_][a-zA-Z0-9_]*)\}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("case_file", type=Path)
    parser.add_argument("base_url", type=str)
    parser.add_argument(
        "--context",
        type=Path,
        required=True,
        help="Server context JSON written by the server target start-server.sh",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def merge_variables(
    case_variables: dict[str, Any] | None,
    server_variables: dict[str, Any],
) -> dict[str, Any]:
    merged = dict(server_variables)
    for key, raw_value in (case_variables or {}).items():
        if isinstance(raw_value, str):
            server_match = SERVER_VARIABLE_PATTERN.match(raw_value)
            if server_match is not None:
                server_key = server_match.group(1)
                if server_key not in server_variables:
                    raise ValueError(f"server variable '{server_key}' is not available")
                merged[key] = server_variables[server_key]
                continue
        merged[key] = raw_value
    return merged


def substitute_value(value: Any, variables: dict[str, Any]) -> Any:
    if isinstance(value, str):

        def replace(match: re.Match[str]) -> str:
            name = match.group(1)
            if name not in variables:
                raise ValueError(f"unknown variable '{name}'")
            return str(variables[name])

        return VARIABLE_PATTERN.sub(replace, value)
    if isinstance(value, list):
        return [substitute_value(item, variables) for item in value]
    if isinstance(value, dict):
        return {key: substitute_value(item, variables) for key, item in value.items()}
    return value


def json_pointer_get(document: Any, pointer: str) -> Any:
    if not pointer.startswith("$."):
        raise ValueError(f"unsupported capture pointer '{pointer}' (expected '$.…')")
    current: Any = document
    for segment in pointer[2:].split("."):
        if not isinstance(current, dict):
            raise ValueError(f"capture pointer '{pointer}' did not resolve to an object")
        if segment not in current:
            raise ValueError(f"capture pointer '{pointer}' missing segment '{segment}'")
        current = current[segment]
    return current


def matches_expected(actual: Any, expected: Any, path: str) -> None:
    if isinstance(expected, dict):
        if "$type" in expected or "$exists" in expected or "$minLength" in expected:
            if expected.get("$exists") is True:
                return
            expected_type = expected.get("$type")
            if expected_type == "string":
                if not isinstance(actual, str):
                    raise AssertionError(f"{path}: expected string, got {type(actual).__name__}")
                min_length = expected.get("$minLength")
                if isinstance(min_length, int) and len(actual) < min_length:
                    raise AssertionError(f"{path}: expected minLength {min_length}, got {len(actual)}")
                return
            if expected_type == "integer":
                if not isinstance(actual, int) or isinstance(actual, bool):
                    raise AssertionError(f"{path}: expected integer, got {actual!r}")
                return
            if expected_type == "boolean":
                if not isinstance(actual, bool):
                    raise AssertionError(f"{path}: expected boolean, got {actual!r}")
                return
            raise AssertionError(f"{path}: unsupported matcher {expected!r}")
        if isinstance(actual, dict):
            for key, expected_value in expected.items():
                if key not in actual:
                    raise AssertionError(f"{path}.{key}: missing key in response")
                matches_expected(actual[key], expected_value, f"{path}.{key}")
            return
        raise AssertionError(f"{path}: expected object, got {type(actual).__name__}")
    if isinstance(expected, list):
        if not isinstance(actual, list):
            raise AssertionError(f"{path}: expected array, got {type(actual).__name__}")
        if len(actual) != len(expected):
            raise AssertionError(f"{path}: expected array length {len(expected)}, got {len(actual)}")
        for index, expected_item in enumerate(expected):
            matches_expected(actual[index], expected_item, f"{path}[{index}]")
        return
    if actual != expected:
        raise AssertionError(f"{path}: expected {expected!r}, got {actual!r}")


def run_case(case_file: Path, base_url: str, context_file: Path) -> None:
    case = load_json(case_file)
    if case.get("schema") != "smithystache.example.test-case/v1":
        raise ValueError(f"unsupported test case schema in {case_file}")

    context = load_json(context_file)
    server_variables = context.get("variables") or {}
    variables = merge_variables(case.get("variables"), server_variables)

    timeout = httpx.Timeout(30.0)
    with httpx.Client(base_url=base_url.rstrip("/"), timeout=timeout) as client:
        for index, step in enumerate(case["steps"], start=1):
            step_id = step.get("id") or f"step-{index}"
            request_spec = substitute_value(step["request"], variables)
            expect_spec = substitute_value(step.get("expect") or {}, variables)

            headers = request_spec.get("headers") or {}
            json_body = request_spec.get("json")
            raw_body = request_spec.get("body")

            response = client.request(
                request_spec["method"],
                request_spec["path"],
                headers=headers,
                json=json_body,
                content=None if json_body is not None else raw_body,
            )

            expected_status = expect_spec["status"]
            if response.status_code != expected_status:
                raise AssertionError(
                    f"{step_id}: expected status {expected_status}, got {response.status_code}: {response.text}"
                )

            expected_headers = expect_spec.get("headers") or {}
            for header_name, expected_value in expected_headers.items():
                actual_value = response.headers.get(header_name)
                if actual_value != expected_value:
                    raise AssertionError(
                        f"{step_id}: header {header_name}: expected {expected_value!r}, got {actual_value!r}"
                    )

            if "json" in expect_spec:
                response_json = response.json()
                matches_expected(response_json, expect_spec["json"], f"{step_id}.json")

            if "body" in expect_spec:
                if response.text != expect_spec["body"]:
                    raise AssertionError(
                        f"{step_id}: body expected {expect_spec['body']!r}, got {response.text!r}"
                    )

            for variable_name, pointer in (step.get("capture") or {}).items():
                captured = json_pointer_get(response.json(), pointer)
                variables[variable_name] = captured


def main() -> int:
    args = parse_args()
    try:
        run_case(args.case_file, args.base_url, args.context)
    except (AssertionError, ValueError, httpx.HTTPError) as error:
        print(f"{args.case_file.name}: {error}", file=sys.stderr)
        return 1
    print(f"{args.case_file.name}: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
