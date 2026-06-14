"""Repository-backed petstore operations used by generated HTTP protocol adapters."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from generated.petstore_api.models.category_detail import CategoryDetail
from generated.petstore_api.models.category_summary import CategorySummary
from generated.petstore_api.models.create_pet_input import CreatePetInput
from generated.petstore_api.models.order_detail import OrderDetail
from generated.petstore_api.models.order_line_detail import OrderLineDetail
from generated.petstore_api.models.order_priority import OrderPriority
from generated.petstore_api.models.order_status import OrderStatus
from generated.petstore_api.models.owner_summary import OwnerSummary
from generated.petstore_api.models.pet_attribute import PetAttribute
from generated.petstore_api.models.pet_attribute_value import PetAttributeValue
from generated.petstore_api.models.pet_detail import PetDetail
from generated.petstore_api.models.pet_profile_summary import PetProfileSummary
from generated.petstore_api.models.pet_species import PetSpecies
from generated.petstore_api.models.pet_status import PetStatus
from generated.petstore_api.models.place_order_input import PlaceOrderInput
from generated.petstore_api.models.postal_address import PostalAddress
from generated.petstore_api.models.store_summary import StoreSummary
from generated.petstore_api.models.update_pet_body import UpdatePetBody
from pet_repository_models import PetHighlight as GeneratedPetHighlight
from pet_repository_models import PetTags as GeneratedPetTags

from server.database import RepositoryBundle


def _postal_from_generated(address: Any) -> PostalAddress:
    return PostalAddress(
        street=address.street,
        city=address.city,
        postal_code=address.postal_code,
    )


def _attribute_value_from_union(value: PetAttributeValue) -> GeneratedPetHighlight:
    if "color" in value:
        return GeneratedPetHighlight(name="color", color=value["color"])
    if "weight_kg" in value:
        return GeneratedPetHighlight(name="weight", color=str(value["weight_kg"]))
    if "vaccinated" in value:
        return GeneratedPetHighlight(name="vaccinated", color=str(value["vaccinated"]))
    return GeneratedPetHighlight(name="unknown", color="")


def _attribute_value_to_union(attribute: GeneratedPetHighlight) -> PetAttributeValue:
    return {"color": attribute.color}


class PetstoreRepositoryService:
    def __init__(self, repositories: RepositoryBundle) -> None:
        self._repositories = repositories

    async def create_pet(self, request: CreatePetInput) -> str:
        adopted_at = request.adopted_at or datetime.now(tz=UTC)
        featured = _attribute_value_from_union(request.attributes[0].value)
        return await self._repositories.pets.create_pet_record(
            name=request.name,
            status=str(request.status.value),
            species=int(request.species.value),
            category_id=request.category_id,
            owner_id=request.owner_id or "",
            tag_count=request.tag_count,
            tags=GeneratedPetTags(items=request.tags),
            featured_attribute=featured,
            photo=request.photo or b"",
            adopted_at=adopted_at,
        )

    async def get_pet(self, pet_id: str) -> PetDetail | None:
        record = await self._repositories.pets.get_pet_record(id=pet_id)
        if record is None:
            return None
        profile = record.pet_profiles[0] if record.pet_profiles else None
        owner = (
            OwnerSummary(
                id=record.owner.id,
                full_name=record.owner.full_name,
                mailing_address=_postal_from_generated(record.owner.mailing_address),
                created_at=record.owner.created_at,
            )
            if record.owner is not None
            else None
        )
        return PetDetail(
            id=record.id,
            name=record.name,
            status=PetStatus(record.status),
            species=PetSpecies(record.species),
            category_id=record.category_id,
            owner_id=record.owner_id or None,
            tag_count=record.tag_count,
            tags=list(record.tags.items),
            attributes=[
                PetAttribute(
                    name=record.featured_attribute.name,
                    value=_attribute_value_to_union(record.featured_attribute),
                )
            ],
            photo=record.photo or None,
            metadata=None,
            adopted_at=record.adopted_at,
            created_at=record.created_at,
            updated_at=record.updated_at,
            category=CategorySummary(
                id=record.category.id,
                name=record.category.name,
                store_id=record.category.store_id,
            ),
            store=StoreSummary(id=record.store.id, name=record.store.name),
            owner=owner,
            profile=(
                PetProfileSummary(id=profile.id, biography=profile.biography, pet_id=profile.pet_id)
                if profile is not None
                else None
            ),
        )

    async def update_pet(self, pet_id: str, request: UpdatePetBody) -> bool:
        adopted_at = request.adopted_at or datetime.now(tz=UTC)
        featured = _attribute_value_from_union(request.attributes[0].value)
        return await self._repositories.pets.update_pet_record(
            name=request.name,
            status=str(request.status.value),
            species=int(request.species.value),
            category_id=request.category_id,
            owner_id=request.owner_id or "",
            tag_count=request.tag_count,
            tags=GeneratedPetTags(items=request.tags),
            featured_attribute=featured,
            photo=request.photo or b"",
            adopted_at=adopted_at,
            id=pet_id,
        )

    async def delete_pet(self, pet_id: str) -> bool:
        return await self._repositories.pets.delete_pet_record(id=pet_id)

    async def get_category(self, category_id: str) -> CategoryDetail | None:
        record = await self._repositories.categories.get_category_record(id=category_id)
        if record is None:
            return None
        return CategoryDetail(
            id=record.id,
            name=record.name,
            store_id=record.store_id,
            store=StoreSummary(id=record.store.id, name=record.store.name),
        )

    async def place_order(self, request: PlaceOrderInput) -> str:
        return await self._repositories.orders.create_order_record(
            label=request.label,
            status=str(request.status.value),
            priority=int(request.priority.value),
        )

    async def get_order(self, order_id: str) -> OrderDetail | None:
        record = await self._repositories.orders.get_order_record(id=order_id)
        if record is None:
            return None
        return OrderDetail(
            id=record.id,
            label=record.label,
            status=OrderStatus(record.status),
            priority=OrderPriority(record.priority),
            created_at=record.created_at,
            updated_at=record.updated_at,
            lines=[
                OrderLineDetail(
                    id=line.id,
                    order_id=line.order_id,
                    pet_id=line.pet_id,
                    quantity=line.quantity,
                    unit_price_cents=line.unit_price_cents,
                    fulfillment=dict(line.fulfillment),
                )
                for line in record.order_lines
            ],
        )

    async def seed_reference_data(self) -> tuple[str, str]:
        store_id = await self._seed_store()
        category_id = await self._repositories.categories.create_category_record(
            name="Dogs",
            store_id=store_id,
        )
        return store_id, category_id

    async def _seed_store(self) -> str:
        connection = self._repositories.connection
        cursor = await connection.execute(
            "INSERT INTO stores (name) VALUES (?) RETURNING id;",
            ("Demo Store",),
        )
        row = await cursor.fetchone()
        if row is None:
            raise RuntimeError("failed to seed store")
        await connection.commit()
        return str(row[0])
