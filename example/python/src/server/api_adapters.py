"""Implement generated HTTP API protocols against repository services."""

from __future__ import annotations

from generated.petstore_api.apis.categories_api_base import CategoriesApiServiceProtocol
from generated.petstore_api.apis.health_api_base import HealthApiServiceProtocol
from generated.petstore_api.apis.orders_api_base import OrdersApiServiceProtocol
from generated.petstore_api.apis.pets_api_base import PetsApiServiceProtocol
from generated.petstore_api.models.category_not_found import CategoryNotFound
from generated.petstore_api.models.create_pet_input import CreatePetInput
from generated.petstore_api.models.create_pet_output import CreatePetOutput
from generated.petstore_api.models.get_category_output import GetCategoryOutput
from generated.petstore_api.models.get_order_output import GetOrderOutput
from generated.petstore_api.models.get_pet_output import GetPetOutput
from generated.petstore_api.models.health_check_output import HealthCheckOutput
from generated.petstore_api.models.order_not_found import OrderNotFound
from generated.petstore_api.models.pet_location_redirect import PetLocationRedirect
from generated.petstore_api.models.pet_not_found import PetNotFound
from generated.petstore_api.models.place_order_input import PlaceOrderInput
from generated.petstore_api.models.place_order_output import PlaceOrderOutput
from generated.petstore_api.models.update_pet_body import UpdatePetBody
from generated.petstore_api.models.update_pet_output import UpdatePetOutput

from server.repository_service import PetstoreRepositoryService


class PetsApiService(PetsApiServiceProtocol):
    def __init__(self, repository_service: PetstoreRepositoryService) -> None:
        self._repository_service = repository_service

    async def create_pet(self, create_pet_input: CreatePetInput) -> CreatePetOutput:
        pet_id = await self._repository_service.create_pet(create_pet_input)
        return CreatePetOutput(id=pet_id, etag=f'W/"{pet_id}"')

    async def resolve_pet_location(self, pet_id: str) -> PetLocationRedirect | PetNotFound:
        pet = await self._repository_service.get_pet(pet_id)
        if pet is None:
            return PetNotFound(message=f"Pet {pet_id} not found")
        return PetLocationRedirect(url=f"/pets/{pet_id}")

    async def delete_pet(self, pet_id: str) -> PetNotFound | None:
        deleted = await self._repository_service.delete_pet(pet_id)
        if not deleted:
            return PetNotFound(message=f"Pet {pet_id} not found")
        return None

    async def get_pet(self, pet_id: str) -> GetPetOutput | PetNotFound:
        pet = await self._repository_service.get_pet(pet_id)
        if pet is None:
            return PetNotFound(message=f"Pet {pet_id} not found")
        return GetPetOutput(pet=pet)

    async def update_pet(
        self,
        pet_id: str,
        body: UpdatePetBody,
    ) -> UpdatePetOutput | PetNotFound:
        updated = await self._repository_service.update_pet(pet_id, body)
        if not updated:
            return PetNotFound(message=f"Pet {pet_id} not found")
        return UpdatePetOutput(updated=updated)


class CategoriesApiService(CategoriesApiServiceProtocol):
    def __init__(self, repository_service: PetstoreRepositoryService) -> None:
        self._repository_service = repository_service

    async def get_category(self, category_id: str) -> GetCategoryOutput | CategoryNotFound:
        category = await self._repository_service.get_category(category_id)
        if category is None:
            return CategoryNotFound(message=f"Category {category_id} not found")
        return GetCategoryOutput(category=category)


class OrdersApiService(OrdersApiServiceProtocol):
    def __init__(self, repository_service: PetstoreRepositoryService) -> None:
        self._repository_service = repository_service

    async def get_order(self, order_id: str) -> GetOrderOutput | OrderNotFound:
        order = await self._repository_service.get_order(order_id)
        if order is None:
            return OrderNotFound(message=f"Order {order_id} not found")
        return GetOrderOutput(order=order)

    async def place_order(self, place_order_input: PlaceOrderInput) -> PlaceOrderOutput:
        order_id = await self._repository_service.place_order(place_order_input)
        return PlaceOrderOutput(id=order_id)


class HealthApiService(HealthApiServiceProtocol):
    async def health_check(self) -> HealthCheckOutput:
        return HealthCheckOutput(status="ok")
