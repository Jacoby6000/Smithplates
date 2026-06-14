"""HTTP client for the petstore reference API (OpenAPI Generator asyncio client)."""

from __future__ import annotations

from typing import Any

from petstore_client import ApiClient, Configuration
from petstore_client.api.default_api import DefaultApi
from petstore_client.models.create_pet_request_content import CreatePetRequestContent
from petstore_client.models.place_order_request_content import PlaceOrderRequestContent
from petstore_client.models.update_pet_body import UpdatePetBody


class PetstoreClient:
    """Thin facade over the generated OpenAPI client with dict-friendly helpers."""

    def __init__(self, base_url: str, *, timeout: float = 30.0) -> None:
        configuration = Configuration(host=base_url.rstrip("/"), ignore_operation_servers=True)
        self._timeout = timeout
        self._api_client = ApiClient(configuration=configuration)
        self._api = DefaultApi(self._api_client)

    async def close(self) -> None:
        await self._api_client.close()

    async def __aenter__(self) -> PetstoreClient:
        return self

    async def __aexit__(self, *args: object) -> None:
        await self.close()

    async def health_check(self) -> dict[str, str]:
        response = await self._api.health_check(_request_timeout=self._timeout)
        return response.to_dict()

    async def create_pet(self, payload: dict[str, Any]) -> str:
        request = CreatePetRequestContent.from_dict(payload)
        if request is None:
            msg = "create_pet payload must be a mapping"
            raise ValueError(msg)
        response = await self._api.create_pet(request, _request_timeout=self._timeout)
        return response.id

    async def get_pet(self, pet_id: str) -> dict[str, Any]:
        response = await self._api.get_pet(pet_id, _request_timeout=self._timeout)
        return response.pet.to_dict()

    async def update_pet(self, pet_id: str, payload: dict[str, Any]) -> bool:
        body = UpdatePetBody.from_dict(payload)
        if body is None:
            msg = "update_pet payload must be a mapping"
            raise ValueError(msg)
        response = await self._api.update_pet(pet_id, body, _request_timeout=self._timeout)
        return response.updated

    async def delete_pet(self, pet_id: str) -> None:
        await self._api.delete_pet(pet_id, _request_timeout=self._timeout)

    async def get_category(self, category_id: str) -> dict[str, Any]:
        response = await self._api.get_category(category_id, _request_timeout=self._timeout)
        return response.category.to_dict()

    async def place_order(self, payload: dict[str, Any]) -> str:
        request = PlaceOrderRequestContent.from_dict(payload)
        if request is None:
            msg = "place_order payload must be a mapping"
            raise ValueError(msg)
        response = await self._api.place_order(request, _request_timeout=self._timeout)
        return response.id

    async def get_order(self, order_id: str) -> dict[str, Any]:
        response = await self._api.get_order(order_id, _request_timeout=self._timeout)
        return response.order.to_dict()
