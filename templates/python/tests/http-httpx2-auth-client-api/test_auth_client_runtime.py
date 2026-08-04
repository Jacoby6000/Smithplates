from __future__ import annotations

import sys
from pathlib import Path
from types import ModuleType
from typing import Any, ClassVar, cast

import pytest

CASE_DIR = Path(__file__).parent
sys.path.insert(0, str(CASE_DIR / "expected" / "src"))

httpx2 = ModuleType("httpx2")
httpx2.AsyncClient = object  # type: ignore[attr-defined]
httpx2.Client = object  # type: ignore[attr-defined]
httpx2.Response = object  # type: ignore[attr-defined]
sys.modules.setdefault("httpx2", httpx2)

from generated.example.auth_api.client.client_registry import create_api_clients  # noqa: E402
from generated.example.auth_api.client.operation_bindings import AuthCredential  # noqa: E402
from generated.example.auth_api.client.sync_client_registry import create_sync_api_clients  # noqa: E402
from generated.example.query_auth_api.client.operation_bindings import (  # noqa: E402
    AuthCredential as QueryAuthCredential,
)
from generated.example.query_auth_api.client.sync_client_registry import (  # noqa: E402
    create_sync_api_clients as create_query_clients,
)

BEARER = "smithy.api#httpBearerAuth"
API_KEY = "smithy.api#httpApiKeyAuth"
COOKIE = "smithplates.codegen.http#httpCookieAuth"


class Response:
    status_code = 200
    headers: ClassVar[dict[str, str]] = {"Content-Type": "application/json"}
    content = b"{}"

    def json(self) -> dict[str, bool]:
        return {"authenticated": True}

    def raise_for_status(self) -> None:
        return None


class SyncTransport:
    def __init__(self) -> None:
        self.requests: list[tuple[str, str, dict[str, str] | None]] = []

    def request(self, method: str, url: str, *, headers: dict[str, str] | None = None, **_: object) -> Response:
        self.requests.append((method, url, headers))
        return Response()


class AsyncTransport:
    def __init__(self) -> None:
        self.requests: list[tuple[str, str, dict[str, str] | None]] = []

    async def request(self, method: str, url: str, *, headers: dict[str, str] | None = None, **_: object) -> Response:
        self.requests.append((method, url, headers))
        return Response()


class Provider:
    def __init__(self, credentials: dict[str, AuthCredential]) -> None:
        self.credentials = credentials
        self.resolved: list[str] = []

    def resolve_auth(self, scheme_id: str) -> AuthCredential | None:
        self.resolved.append(scheme_id)
        return self.credentials.get(scheme_id)


class QueryProvider:
    def __init__(self, credentials: dict[str, QueryAuthCredential]) -> None:
        self.credentials = credentials

    def resolve_auth(self, scheme_id: str) -> QueryAuthCredential | None:
        return self.credentials.get(scheme_id)


def test_sync_auth_applies_ordered_header_query_and_cookie_credentials() -> None:
    transport = SyncTransport()
    provider = Provider({API_KEY: AuthCredential(API_KEY, "secret")})
    clients = create_sync_api_clients(cast(Any, transport), base_url="https://example.test/", auth_provider=provider)

    clients.auth_client.required(filter="active", trace="trace-1")

    assert provider.resolved == [BEARER, API_KEY]
    _, url, headers = transport.requests[-1]
    assert url == "https://example.test/required?filter=active"
    assert headers == {"X-Trace": "trace-1", "X-API-Key": "ApiKey secret"}

    cookie_transport = SyncTransport()
    cookie_provider = Provider({COOKIE: AuthCredential(COOKIE, "session-value")})
    cookie_clients = create_sync_api_clients(
        cast(Any, cookie_transport),
        base_url="https://example.test",
        auth_provider=cookie_provider,
    )
    cookie_clients.auth_client.cookie_only()
    assert cookie_transport.requests[-1][2] == {"Cookie": "session=session-value"}

    query_transport = SyncTransport()
    query_provider = QueryProvider({API_KEY: QueryAuthCredential(API_KEY, "query secret")})
    query_clients = create_query_clients(
        cast(Any, query_transport),
        base_url="https://example.test",
        auth_provider=query_provider,
    )
    query_clients.query_auth_client.query_api_key(filter="modeled")
    assert query_transport.requests[-1][1] == "https://example.test/query-key?filter=modeled&api_key=query%20secret"


@pytest.mark.asyncio
async def test_async_auth_and_anonymous_operations() -> None:
    transport = AsyncTransport()
    provider = Provider({BEARER: AuthCredential(BEARER, "token")})
    clients = create_api_clients(cast(Any, transport), base_url="https://example.test", auth_provider=provider)

    await clients.auth_client.bearer_only()
    await clients.auth_client.optional()
    await clients.auth_client.public()

    assert transport.requests[0][2] == {"Authorization": "Bearer token"}
    assert len(transport.requests) == 3


def test_missing_and_invalid_credentials_fail_before_transport() -> None:
    transport = SyncTransport()
    clients = create_sync_api_clients(cast(Any, transport), base_url="https://example.test")

    with pytest.raises(RuntimeError, match="No usable authentication credential"):
        clients.auth_client.bearer_only()
    assert transport.requests == []

    clients.auth_client.optional()
    clients.auth_client.public()
    assert len(transport.requests) == 2

    invalid_provider = Provider({BEARER: AuthCredential("example#invalid", "token")})
    invalid_clients = create_sync_api_clients(
        cast(Any, transport),
        base_url="https://example.test",
        auth_provider=invalid_provider,
    )
    with pytest.raises(ValueError, match="not allowed by the operation"):
        invalid_clients.auth_client.bearer_only()
    assert len(transport.requests) == 2

    invalid_cookie_provider = Provider({COOKIE: AuthCredential(COOKIE, "value; injected=true")})
    invalid_cookie_clients = create_sync_api_clients(
        cast(Any, transport),
        base_url="https://example.test",
        auth_provider=invalid_cookie_provider,
    )
    with pytest.raises(ValueError, match="invalid character"):
        invalid_cookie_clients.auth_client.cookie_only()
    assert len(transport.requests) == 2
