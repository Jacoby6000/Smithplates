# Generated from example#AccountRepository by sql-service-codegen. Do not edit by hand.
from __future__ import annotations

from dataclasses import dataclass

ExternalId = str
TenantId = str


@dataclass
class Account:
    id: str
    tenant_id: TenantId | None
    external_id: ExternalId | None
