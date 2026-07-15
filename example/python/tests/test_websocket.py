"""End-to-end WebSocket smoke test for the petstore reference server.

Launches the real FastAPI app under uvicorn on an ephemeral port and exercises
the generated `PetstoreWebsocketClient` against it:
* ping/pong round-trip (client-to-server plus server-to-client message).
* subscribe returns the live pet status for a pet created via the REST API
  (exercises the integration between REST seeding and WebSocket reads).
"""

from __future__ import annotations

import asyncio
import contextlib
import socket
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any

import pytest
import uvicorn
from httpx import AsyncClient

from generated.petstore.api.clients.websocket_client import PetstoreWebsocketClient
from generated.petstore.api.pet_events_input import PetEventsInput
from generated.petstore.api.pet_ping import PetPing
from generated.petstore.api.pet_subscription import PetSubscription
from server.app import build_app


def _free_port() -> int:
    with contextlib.closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
        sock.bind(("127.0.0.1", 0))
        _host, port = sock.getsockname()
        return int(port)


@pytest.fixture
async def petstore_server(tmp_path: Path) -> AsyncIterator[tuple[AsyncClient, str, str]]:
    database_path = tmp_path / "petstore.sqlite3"
    app = build_app(database_path=database_path)
    port = _free_port()
    config = uvicorn.Config(app, host="127.0.0.1", port=port, log_level="warning", lifespan="on")
    server = uvicorn.Server(config)

    server_task = asyncio.create_task(server.serve())
    # Wait until uvicorn reports the socket is accepting connections.
    while not server.started:
        await asyncio.sleep(0.01)

    base_url = f"http://127.0.0.1:{port}"
    ws_base_url = f"ws://127.0.0.1:{port}"
    try:
        async with AsyncClient(base_url=base_url) as client:
            yield client, ws_base_url, app.state.seed_category_id
    finally:
        server.should_exit = True
        await server_task


@pytest.fixture
async def seeded_pet_id(
    petstore_server: tuple[AsyncClient, str, str],
) -> AsyncIterator[str]:
    client, _, seed_category_id = petstore_server
    create_payload: dict[str, Any] = {
        "name": "Comet",
        "status": "available",
        "species": 1,
        "category_id": seed_category_id,
        "owner_id": None,
        "tag_count": 2,
        "tags": ["friendly", "trained"],
        "attributes": [
            {"name": "color", "value": {"color": "golden"}},
        ],
        "metadata": {"notes": "demo pet"},
    }
    resp = await client.post("/pets", json=create_payload)
    assert resp.status_code == 201
    pet_id = resp.json()["id"]
    assert pet_id
    yield pet_id


@pytest.mark.asyncio
async def test_websocket_ping_pong(petstore_server: tuple[AsyncClient, str, str]) -> None:
    _, ws_base_url, _ = petstore_server
    client = PetstoreWebsocketClient(ws_base_url)
    nonce = "abc-123"

    async with await client.connect_pet_events() as connection:
        await connection.send(PetEventsInput(ping=PetPing(nonce=nonce)))
        message = await asyncio.wait_for(connection.receive(), timeout=2.0)
        assert message.pong is not None
        assert message.pong.nonce == nonce


@pytest.mark.asyncio
async def test_websocket_subscribe_returns_pet_status(
    petstore_server: tuple[AsyncClient, str, str],
    seeded_pet_id: str,
) -> None:
    _, ws_base_url, _ = petstore_server
    client = PetstoreWebsocketClient(ws_base_url)

    async with await client.connect_pet_events() as connection:
        await connection.send(
            PetEventsInput(subscribe=PetSubscription(pet_id=seeded_pet_id)),
        )
        message = await asyncio.wait_for(connection.receive(), timeout=2.0)
        assert message.statusChanged is not None
        assert message.statusChanged.pet_id == seeded_pet_id
        assert message.statusChanged.status.value == "available"
