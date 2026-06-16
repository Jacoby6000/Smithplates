#!/usr/bin/env python3
"""Execute a language-neutral HTTP test case via the generated OpenAPI client."""

from __future__ import annotations

import argparse
import asyncio
import json
import re
import sys
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from petstore_client import ApiClient, Configuration
from petstore_client.api.default_api import DefaultApi
from petstore_client.exceptions import ApiException
from petstore_client.models.create_pet_request_content import CreatePetRequestContent
from petstore_client.models.place_order_request_content import PlaceOrderRequestContent
from petstore_client.models.update_pet_body import UpdatePetBody

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


def model_to_json(value: Any) -> dict[str, Any] | None:
    if value is None:
        return None
    if hasattr(value, "to_dict"):
        converted = value.to_dict()
        if isinstance(converted, dict):
            return converted
        raise TypeError(f"{type(value).__name__}.to_dict() returned {type(converted).__name__}, expected dict")
    if isinstance(value, dict):
        return value
    raise TypeError(f"unsupported response payload type: {type(value).__name__}")


@dataclass(frozen=True)
class StepResult:
    status_code: int
    headers: dict[str, str]
    response_json: dict[str, Any] | None
    response_body: str | None


def step_result_from_api_response(response: Any) -> StepResult:
    headers = dict(response.headers or {})
    return StepResult(
        status_code=response.status_code,
        headers=headers,
        response_json=model_to_json(response.data),
        response_body=response.raw_data.decode("utf-8") if response.raw_data else None,
    )


def step_result_from_api_exception(error: ApiException) -> StepResult:
    headers = dict(error.headers or {})
    response_json = model_to_json(error.data) if error.data is not None else None
    return StepResult(
        status_code=error.status or 0,
        headers=headers,
        response_json=response_json,
        response_body=error.body,
    )


def require_path_param(path_params: dict[str, Any], key: str, operation_id: str) -> str:
    value = path_params.get(key)
    if value is None:
        raise ValueError(f"{operation_id}: missing required path parameter '{key}'")
    return str(value)


def deserialize_body(model_cls: Any, json_body: Any, operation_id: str) -> Any:
    if json_body is None:
        raise ValueError(f"{operation_id}: request.json body is required")
    model = model_cls.from_dict(json_body)
    if model is None:
        raise ValueError(f"{operation_id}: request.json did not deserialize into {model_cls.__name__}")
    return model


OperationHandler = Callable[[DefaultApi, dict[str, Any], Any], Awaitable[Any]]


async def _health_check(api: DefaultApi, _path_params: dict[str, Any], _json_body: Any) -> Any:
    return await api.health_check_with_http_info()


async def _create_pet(api: DefaultApi, _path_params: dict[str, Any], json_body: Any) -> Any:
    body = deserialize_body(CreatePetRequestContent, json_body, "CreatePet")
    return await api.create_pet_with_http_info(create_pet_request_content=body)


async def _get_pet(api: DefaultApi, path_params: dict[str, Any], _json_body: Any) -> Any:
    pet_id = require_path_param(path_params, "petId", "GetPet")
    return await api.get_pet_with_http_info(pet_id=pet_id)


async def _update_pet(api: DefaultApi, path_params: dict[str, Any], json_body: Any) -> Any:
    pet_id = require_path_param(path_params, "petId", "UpdatePet")
    body = deserialize_body(UpdatePetBody, json_body, "UpdatePet")
    return await api.update_pet_with_http_info(pet_id=pet_id, update_pet_body=body)


async def _delete_pet(api: DefaultApi, path_params: dict[str, Any], _json_body: Any) -> Any:
    pet_id = require_path_param(path_params, "petId", "DeletePet")
    return await api.delete_pet_with_http_info(pet_id=pet_id)


async def _get_category(api: DefaultApi, path_params: dict[str, Any], _json_body: Any) -> Any:
    category_id = require_path_param(path_params, "categoryId", "GetCategory")
    return await api.get_category_with_http_info(category_id=category_id)


async def _place_order(api: DefaultApi, _path_params: dict[str, Any], json_body: Any) -> Any:
    body = deserialize_body(PlaceOrderRequestContent, json_body, "PlaceOrder")
    return await api.place_order_with_http_info(place_order_request_content=body)


async def _get_order(api: DefaultApi, path_params: dict[str, Any], _json_body: Any) -> Any:
    order_id = require_path_param(path_params, "orderId", "GetOrder")
    return await api.get_order_with_http_info(order_id=order_id)


OPERATION_HANDLERS: dict[str, OperationHandler] = {
    "HealthCheck": _health_check,
    "CreatePet": _create_pet,
    "GetPet": _get_pet,
    "UpdatePet": _update_pet,
    "DeletePet": _delete_pet,
    "GetCategory": _get_category,
    "PlaceOrder": _place_order,
    "GetOrder": _get_order,
}


def header_value(headers: dict[str, str], name: str) -> str | None:
    name_lower = name.lower()
    for key, value in headers.items():
        if key.lower() == name_lower:
            return value
    return None


async def execute_raw_request(base_url: str, request_spec: dict[str, Any]) -> StepResult:
    import httpx

    method = request_spec["method"]
    path = request_spec["path"]
    url = f"{base_url.rstrip('/')}{path}"
    headers = request_spec.get("headers") or {}
    json_body = request_spec.get("json")
    async with httpx.AsyncClient(follow_redirects=False) as client:
        response = await client.request(method, url, headers=headers, json=json_body)

    response_json: dict[str, Any] | None = None
    if response.content:
        try:
            parsed = response.json()
            if isinstance(parsed, dict):
                response_json = parsed
        except json.JSONDecodeError:
            response_json = None

    return StepResult(
        status_code=response.status_code,
        headers=dict(response.headers),
        response_json=response_json,
        response_body=response.text if response.text else None,
    )


async def execute_request(api: DefaultApi, request_spec: dict[str, Any]) -> StepResult:
    operation_id = request_spec.get("operationId")
    if not operation_id:
        method = request_spec.get("method")
        path = request_spec.get("path")
        raise ValueError(f"request for {method} {path} is missing operationId")

    handler = OPERATION_HANDLERS.get(operation_id)
    if handler is None:
        known = ", ".join(sorted(OPERATION_HANDLERS))
        raise ValueError(f"unsupported operationId '{operation_id}' (known: {known})")

    path_params = request_spec.get("pathParameters") or {}
    json_body = request_spec.get("json")
    try:
        response = await handler(api, path_params, json_body)
    except ApiException as error:
        return step_result_from_api_exception(error)
    return step_result_from_api_response(response)


def apply_captures(
    step_id: str,
    step: dict[str, Any],
    result: StepResult,
    variables: dict[str, Any],
) -> None:
    for variable_name, pointer in (step.get("capture") or {}).items():
        if result.response_json is None:
            raise AssertionError(f"{step_id}: cannot capture from empty response body")
        captured = json_pointer_get(result.response_json, pointer)
        variables[variable_name] = captured


def assert_step_expectations(
    step_id: str,
    expect_spec: dict[str, Any],
    result: StepResult,
    variables: dict[str, Any],
) -> None:
    expected_status = substitute_value(expect_spec["status"], variables)
    if result.status_code != expected_status:
        detail = result.response_body or result.response_json
        raise AssertionError(f"{step_id}: expected status {expected_status}, got {result.status_code}: {detail}")

    expected_headers = substitute_value(expect_spec.get("headers") or {}, variables)
    for header_name, expected_value in expected_headers.items():
        actual_value = header_value(result.headers, header_name)
        if actual_value != expected_value:
            raise AssertionError(
                f"{step_id}: header {header_name}: expected {expected_value!r}, got {actual_value!r}"
            )

    if "json" in expect_spec:
        if result.response_json is None:
            raise AssertionError(f"{step_id}: expected JSON response body, got none")
        expected_json = substitute_value(expect_spec["json"], variables)
        matches_expected(result.response_json, expected_json, f"{step_id}.json")

    if "body" in expect_spec:
        expected_body = substitute_value(expect_spec["body"], variables)
        if result.response_body != expected_body:
            raise AssertionError(f"{step_id}: body expected {expected_body!r}, got {result.response_body!r}")


async def run_case(case_file: Path, base_url: str, context_file: Path) -> None:
    case = load_json(case_file)
    if case.get("schema") != "smithystache.example.test-case/v1":
        raise ValueError(f"unsupported test case schema in {case_file}")

    context = load_json(context_file)
    server_variables = context.get("variables") or {}
    variables = merge_variables(case.get("variables"), server_variables)

    configuration = Configuration(host=base_url.rstrip("/"), ignore_operation_servers=True)
    api_client = ApiClient(configuration=configuration)
    api = DefaultApi(api_client)
    try:
        for index, step in enumerate(case["steps"], start=1):
            step_id = step.get("id") or f"step-{index}"
            request_spec = substitute_value(step["request"], variables)
            expect_spec = step.get("expect") or {}

            transport = request_spec.get("transport", "client")
            if transport == "raw":
                result = await execute_raw_request(base_url, request_spec)
            elif transport == "client":
                result = await execute_request(api, request_spec)
            else:
                raise ValueError(f"{step_id}: unsupported request transport '{transport}'")

            apply_captures(step_id, step, result, variables)
            assert_step_expectations(step_id, expect_spec, result, variables)
    finally:
        await api_client.close()


def main() -> int:
    args = parse_args()
    try:
        asyncio.run(run_case(args.case_file, args.base_url, args.context))
    except (AssertionError, ValueError, ApiException) as error:
        print(f"{args.case_file.name}: {error}", file=sys.stderr)
        return 1
    print(f"{args.case_file.name}: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
