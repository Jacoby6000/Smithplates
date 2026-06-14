"""FastAPI server implementing the Smithy petstore HTTP contract."""

from __future__ import annotations

import base64
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Annotated, Any

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response

from server.database import repository_lifespan
from server.services import PetstoreService
from server.types import OrderPriority, OrderStatus, PetAttribute, PetAttributeValue, PetSpecies, PetStatus


def _parse_attributes(raw_items: list[dict[str, Any]]) -> list[PetAttribute]:
    attributes: list[PetAttribute] = []
    for item in raw_items:
        value_payload = item["value"]
        if "color" in value_payload:
            value = PetAttributeValue(color=value_payload["color"])
        elif "weight_kg" in value_payload:
            value = PetAttributeValue(weight_kg=float(value_payload["weight_kg"]))
        elif "vaccinated" in value_payload:
            value = PetAttributeValue(vaccinated=bool(value_payload["vaccinated"]))
        else:
            raise HTTPException(status_code=400, detail={"message": "attribute union requires one value field"})
        attributes.append(PetAttribute(name=item["name"], value=value))
    return attributes


def _parse_create_pet(body: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": body["name"],
        "status": PetStatus(body["status"]),
        "species": PetSpecies(body["species"]),
        "category_id": body["category_id"],
        "owner_id": body.get("owner_id"),
        "tag_count": body["tag_count"],
        "tags": body["tags"],
        "attributes": _parse_attributes(body["attributes"]),
        "featured_attribute": {
            "name": body["attributes"][0]["name"],
            "color": body["attributes"][0]["value"].get("color", "golden"),
        },
        "photo": base64.b64decode(body["photo"]) if body.get("photo") else None,
        "metadata": body.get("metadata"),
        "adopted_at": datetime.fromisoformat(body["adopted_at"]) if body.get("adopted_at") else None,
    }


def _encode(value: dict[str, Any]) -> dict[str, Any]:
    encoded = dict(value)
    if encoded.get("photo") is not None and isinstance(encoded["photo"], (bytes, bytearray)):
        encoded["photo"] = base64.b64encode(encoded["photo"]).decode("ascii")
    for key in ("adopted_at", "created_at", "updated_at"):
        if encoded.get(key) is not None and isinstance(encoded[key], datetime):
            encoded[key] = encoded[key].isoformat()
    return encoded


def _pet_detail_to_json(pet: Any) -> dict[str, Any]:
    payload = _encode(pet.__dict__)
    payload["status"] = str(pet.status)
    payload["species"] = int(pet.species)
    payload["category"] = _encode(pet.category.__dict__)
    payload["store"] = _encode(pet.store.__dict__)
    if pet.owner is not None:
        owner_payload = _encode(pet.owner.__dict__)
        owner_payload["mailing_address"] = _encode(pet.owner.mailing_address.__dict__)
        payload["owner"] = owner_payload
    else:
        payload["owner"] = None
    if pet.profile is not None:
        payload["profile"] = _encode(pet.profile.__dict__)
    else:
        payload["profile"] = None
    payload["attributes"] = [
        {
            "name": attribute.name,
            "value": {key: val for key, val in attribute.value.__dict__.items() if val is not None},
        }
        for attribute in pet.attributes
    ]
    return {"pet": payload}


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with repository_lifespan() as repositories:
        service = PetstoreService(repositories)
        store_id, category_id = await service.seed_reference_data()
        app.state.service = service
        app.state.seed_store_id = store_id
        app.state.seed_category_id = category_id
        yield


app = FastAPI(title="Petstore", version="2024-01-01", lifespan=lifespan)


def get_service(request: Request) -> PetstoreService:
    return request.app.state.service


ServiceDep = Annotated[PetstoreService, Depends(get_service)]


@app.get("/health")
async def health_check() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/pets", status_code=201)
async def create_pet(body: dict[str, Any], service: ServiceDep) -> dict[str, str]:
    pet_id = await service.create_pet(_parse_create_pet(body))
    return {"id": pet_id}


@app.get("/pets/{pet_id}")
async def get_pet(pet_id: str, service: ServiceDep) -> JSONResponse:
    pet = await service.get_pet(pet_id)
    if pet is None:
        raise HTTPException(status_code=404, detail={"message": f"Pet {pet_id} not found"})
    return JSONResponse(_pet_detail_to_json(pet))


@app.put("/pets/{pet_id}")
async def update_pet(pet_id: str, body: dict[str, Any], service: ServiceDep) -> dict[str, bool]:
    from server.types import UpdatePetRequest

    request = UpdatePetRequest(
        name=body["name"],
        status=PetStatus(body["status"]),
        species=PetSpecies(body["species"]),
        category_id=body["category_id"],
        owner_id=body.get("owner_id"),
        tag_count=body["tag_count"],
        tags=body["tags"],
        attributes=_parse_attributes(body["attributes"]),
        photo=base64.b64decode(body["photo"]) if body.get("photo") else None,
        metadata=body.get("metadata"),
        adopted_at=datetime.fromisoformat(body["adopted_at"]) if body.get("adopted_at") else None,
    )
    updated = await service.update_pet(pet_id, request)
    if not updated:
        raise HTTPException(status_code=404, detail={"message": f"Pet {pet_id} not found"})
    return {"updated": updated}


@app.delete("/pets/{pet_id}", status_code=204)
async def delete_pet(pet_id: str, service: ServiceDep) -> Response:
    deleted = await service.delete_pet(pet_id)
    if not deleted:
        raise HTTPException(status_code=404, detail={"message": f"Pet {pet_id} not found"})
    return Response(status_code=204)


@app.get("/categories/{category_id}")
async def get_category(category_id: str, service: ServiceDep) -> JSONResponse:
    category = await service.get_category(category_id)
    if category is None:
        raise HTTPException(status_code=404, detail={"message": f"Category {category_id} not found"})
    payload = _encode(category.__dict__)
    payload["store"] = _encode(category.store.__dict__)
    return JSONResponse({"category": payload})


@app.post("/orders", status_code=201)
async def place_order(body: dict[str, Any], service: ServiceDep) -> dict[str, str]:
    from server.types import PlaceOrderRequest

    request = PlaceOrderRequest(
        label=body["label"],
        status=OrderStatus(body["status"]),
        priority=OrderPriority(body["priority"]),
    )
    order_id = await service.place_order(request)
    return {"id": order_id}


@app.get("/orders/{order_id}")
async def get_order(order_id: str, service: ServiceDep) -> JSONResponse:
    order = await service.get_order(order_id)
    if order is None:
        raise HTTPException(status_code=404, detail={"message": f"Order {order_id} not found"})
    payload = _encode(order.__dict__)
    payload["status"] = str(order.status)
    payload["priority"] = int(order.priority)
    payload["lines"] = [_encode(line.__dict__) for line in order.lines]
    return JSONResponse({"order": payload})


def run() -> None:
    import uvicorn

    uvicorn.run("server.app:app", host="127.0.0.1", port=8080, reload=False)


if __name__ == "__main__":
    run()
