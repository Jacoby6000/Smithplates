"""End-to-end API smoke test for the petstore reference server."""

from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

import pytest
from httpx import ASGITransport, AsyncClient
from server.app import build_app


@pytest.fixture
async def api_client(tmp_path: Path) -> AsyncIterator[tuple[AsyncClient, str]]:
    database_path = tmp_path / "petstore.sqlite3"
    app = build_app(database_path=database_path)
    transport = ASGITransport(app=app)
    async with app.router.lifespan_context(app):
        category_id = app.state.seed_category_id
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            yield client, category_id


@pytest.mark.asyncio
async def test_health_check(api_client: tuple[AsyncClient, str]) -> None:
    client, _category_id = api_client
    response = await client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


@pytest.mark.asyncio
async def test_pet_crud_lifecycle(api_client: tuple[AsyncClient, str]) -> None:
    client, category_id = api_client
    create_payload = {
        "name": "Comet",
        "status": "available",
        "species": 1,
        "category_id": category_id,
        "owner_id": None,
        "tag_count": 2,
        "tags": ["friendly", "trained"],
        "attributes": [
            {"name": "color", "value": {"color": "golden"}},
            {"name": "weight", "value": {"weight_kg": 12.5}},
        ],
        "metadata": {"notes": "demo pet"},
    }
    create_response = await client.post("/pets", json=create_payload)
    assert create_response.status_code == 201
    pet_id = create_response.json()["id"]
    assert pet_id

    get_response = await client.get(f"/pets/{pet_id}")
    assert get_response.status_code == 200
    pet = get_response.json()["pet"]
    assert pet["name"] == "Comet"
    assert pet["status"] == "available"
    assert pet["species"] == 1
    assert pet["category"]["id"] == category_id
    assert pet["store"]["name"] == "Demo Store"

    update_payload = dict(create_payload)
    update_payload["name"] = "Comet Updated"
    update_payload["status"] = "pending"
    update_response = await client.put(f"/pets/{pet_id}", json=update_payload)
    assert update_response.status_code == 200
    assert update_response.json()["updated"] is True

    delete_response = await client.delete(f"/pets/{pet_id}")
    assert delete_response.status_code == 204

    missing_response = await client.get(f"/pets/{pet_id}")
    assert missing_response.status_code == 404
