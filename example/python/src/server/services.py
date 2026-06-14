"""Map between HTTP API types and generated repository services."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from pet_repository_models import PetHighlight as GeneratedPetHighlight
from pet_repository_models import PetTags as GeneratedPetTags

from server.database import RepositoryBundle
from server.types import (
    CategoryDetail,
    CategorySummary,
    OrderDetail,
    OrderLineDetail,
    OrderPriority,
    OrderStatus,
    OwnerSummary,
    PetAttribute,
    PetAttributeValue,
    PetDetail,
    PetProfileSummary,
    PetSpecies,
    PetStatus,
    PlaceOrderRequest,
    PostalAddress,
    StoreSummary,
    UpdatePetRequest,
)



def _postal_from_generated(address: Any) -> PostalAddress:
    return PostalAddress(
        street=address.street,
        city=address.city,
        postal_code=address.postal_code,
    )


class PetstoreService:
    def __init__(self, repositories: RepositoryBundle) -> None:
        self._repositories = repositories

    async def create_pet(self, request: dict[str, Any]) -> str:
        adopted_at = request.get("adopted_at") or datetime.now(tz=UTC)
        return await self._repositories.pets.create_pet_record(
            name=request["name"],
            status=str(request["status"]),
            species=int(request["species"]),
            category_id=request["category_id"],
            owner_id=request.get("owner_id") or "",
            tag_count=request["tag_count"],
            tags=GeneratedPetTags(items=request["tags"]),
            featured_attribute=GeneratedPetHighlight(
                name=request["featured_attribute"]["name"],
                color=request["featured_attribute"]["color"],
            ),
            photo=request.get("photo") or b"",
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
                    value=PetAttributeValue(color=record.featured_attribute.color),
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

    async def update_pet(self, pet_id: str, request: UpdatePetRequest) -> bool:
        adopted_at = request.adopted_at or datetime.now(tz=UTC)
        return await self._repositories.pets.update_pet_record(
            name=request.name,
            status=str(request.status),
            species=int(request.species),
            category_id=request.category_id,
            owner_id=request.owner_id or "",
            tag_count=request.tag_count,
            tags=GeneratedPetTags(items=request.tags),
            featured_attribute=GeneratedPetHighlight(
                name=request.attributes[0].name,
                color=request.attributes[0].value.color or "",
            ),
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

    async def place_order(self, request: PlaceOrderRequest) -> str:
        return await self._repositories.orders.create_order_record(
            label=request.label,
            status=str(request.status),
            priority=int(request.priority),
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
        """Insert a default store and category for interactive demos."""
        store_id = await self._seed_store()
        category_id = await self._repositories.categories.create_category_record(
            name="Dogs",
            store_id=store_id,
        )
        return store_id, category_id

    async def _seed_store(self) -> str:
        # Categories require a store; create one directly until a StoreRepository exists.
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
