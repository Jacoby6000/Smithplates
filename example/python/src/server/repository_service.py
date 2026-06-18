"""Repository-backed petstore operations used by generated HTTP protocol adapters."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from typing import Any, cast

from generated.db.models.pet_repository_models import PetHighlight as GeneratedPetHighlight
from generated.db.models.pet_repository_models import PetTags as GeneratedPetTags
from generated.http.models.category_detail import CategoryDetail
from generated.http.models.category_summary import CategorySummary
from generated.http.models.create_pet_input import CreatePetInput
from generated.http.models.fulfillment_state import FulfillmentState
from generated.http.models.order_detail import OrderDetail
from generated.http.models.order_line_detail import OrderLineDetail
from generated.http.models.order_priority import OrderPriority
from generated.http.models.order_status import OrderStatus
from generated.http.models.owner_summary import OwnerSummary
from generated.http.models.pet_attribute import PetAttribute
from generated.http.models.pet_attribute_value import (
    PetAttributeValue,
    PetAttributeValueColor,
    PetAttributeValueWeight_kg,
)
from generated.http.models.pet_detail import PetDetail
from generated.http.models.pet_profile_summary import PetProfileSummary
from generated.http.models.pet_species import PetSpecies
from generated.http.models.pet_status import PetStatus
from generated.http.models.place_order_input import PlaceOrderInput
from generated.http.models.postal_address import PostalAddress
from generated.http.models.store_summary import StoreSummary
from generated.http.models.update_pet_body import UpdatePetBody
from server.database import RepositoryBundle


def _postal_from_generated(address: Any) -> PostalAddress:
    return PostalAddress(
        street=address.street,
        city=address.city,
        postal_code=address.postal_code,
    )


def _attribute_value_from_union(value: PetAttributeValue) -> GeneratedPetHighlight:
    """Persist the union variant as a (discriminator name, text value) pair in PetHighlight."""
    if "color" in value:
        color_item = cast(PetAttributeValueColor, value)
        return GeneratedPetHighlight(name="color", color=color_item["color"])
    if "weight_kg" in value:
        weight_item = cast(PetAttributeValueWeight_kg, value)
        return GeneratedPetHighlight(name="weight_kg", color=str(weight_item["weight_kg"]))
    if "vaccinated" in value:
        return GeneratedPetHighlight(
            name="vaccinated",
            color="true" if value["vaccinated"] else "false",
        )
    raise ValueError(f"unsupported PetAttributeValue variant: {value!r}")


def _attribute_value_to_union(attribute: GeneratedPetHighlight) -> PetAttributeValue:
    """Reconstruct the union variant from the discriminator persisted in PetHighlight.name."""
    match attribute.name:
        case "color":
            return {"color": attribute.color}
        case "weight_kg":
            return {"weight_kg": float(attribute.color)}
        case "vaccinated":
            return {"vaccinated": attribute.color == "true"}
        case other:
            raise ValueError(f"unsupported PetHighlight discriminator: {other!r}")


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
                    fulfillment=cast(FulfillmentState, dict(line.fulfillment)),
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

    async def seed_fulfillment_orders(self) -> dict[str, str]:
        """Seed one single-line order per FulfillmentState variant; return their ids by variant."""
        shipped_at = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC).isoformat()
        delivered_at = datetime(2026, 2, 3, 4, 5, 6, tzinfo=UTC).isoformat()
        variants: dict[str, dict[str, str]] = {
            "pending": {"pending": "awaiting-stock"},
            "shipped": {"shipped": shipped_at},
            "delivered": {"delivered": delivered_at},
        }
        order_ids: dict[str, str] = {}
        for variant, fulfillment in variants.items():
            order_id = await self._seed_single_line_order(
                label=f"{variant}-order",
                fulfillment=fulfillment,
            )
            order_ids[variant] = order_id
        return order_ids

    async def _seed_single_line_order(self, label: str, fulfillment: dict[str, str]) -> str:
        connection = self._repositories.connection
        order_cursor = await connection.execute(
            "INSERT INTO orders (label, status, priority) VALUES (?, ?, ?) RETURNING id;",
            (label, "placed", 1),
        )
        order_row = await order_cursor.fetchone()
        if order_row is None:
            raise RuntimeError(f"failed to seed order {label!r}")
        order_id = str(order_row[0])
        await connection.execute(
            "INSERT INTO order_lines "
            "(order_id, pet_id, quantity, unit_price_cents, fulfillment) "
            "VALUES (?, ?, ?, ?, ?);",
            (order_id, "seed-pet", 1, 1999, json.dumps(fulfillment)),
        )
        await connection.commit()
        return order_id

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
