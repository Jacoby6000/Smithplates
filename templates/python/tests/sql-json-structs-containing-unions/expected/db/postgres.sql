-- example#Shipment
CREATE TABLE shipments (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,
    destination JSONB,
    state JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Queries

-- example#CreateShipment
INSERT INTO shipments (label, destination, state) VALUES ($1, $2, $3) RETURNING id;

-- example#UpdateShipment
UPDATE shipments
SET label = $1, destination = $2, state = $3
WHERE id = $4;

-- example#DeleteShipment
DELETE FROM shipments WHERE id = $1 RETURNING id;

-- example#GetShipment
SELECT shipments.id, shipments.label, shipments.destination, shipments.state, shipments.created_at
FROM shipments
WHERE id = $1;