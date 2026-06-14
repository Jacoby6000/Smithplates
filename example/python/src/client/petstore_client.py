"""HTTP client for the petstore reference API."""

from __future__ import annotations

from typing import Any

import httpx


class PetstoreClient:
    def __init__(self, base_url: str, *, timeout: float = 30.0) -> None:
        self._client = httpx.AsyncClient(base_url=base_url.rstrip("/"), timeout=timeout)

    async def close(self) -> None:
        await self._client.aclose()

    async def __aenter__(self) -> PetstoreClient:
        return self

    async def __aexit__(self, *args: object) -> None:
        await self.close()

    async def health_check(self) -> dict[str, str]:
        response = await self._client.get("/health")
        response.raise_for_status()
        return response.json()

    async def create_pet(self, payload: dict[str, Any]) -> str:
        response = await self._client.post("/pets", json=payload)
        response.raise_for_status()
        return response.json()["id"]

    async def get_pet(self, pet_id: str) -> dict[str, Any]:
        response = await self._client.get(f"/pets/{pet_id}")
        response.raise_for_status()
        return response.json()["pet"]

    async def update_pet(self, pet_id: str, payload: dict[str, Any]) -> bool:
        response = await self._client.put(f"/pets/{pet_id}", json=payload)
        response.raise_for_status()
        return response.json()["updated"]

    async def delete_pet(self, pet_id: str) -> None:
        response = await self._client.delete(f"/pets/{pet_id}")
        response.raise_for_status()

    async def get_category(self, category_id: str) -> dict[str, Any]:
        response = await self._client.get(f"/categories/{category_id}")
        response.raise_for_status()
        return response.json()["category"]

    async def place_order(self, payload: dict[str, Any]) -> str:
        response = await self._client.post("/orders", json=payload)
        response.raise_for_status()
        return response.json()["id"]

    async def get_order(self, order_id: str) -> dict[str, Any]:
        response = await self._client.get(f"/orders/{order_id}")
        response.raise_for_status()
        return response.json()["order"]
