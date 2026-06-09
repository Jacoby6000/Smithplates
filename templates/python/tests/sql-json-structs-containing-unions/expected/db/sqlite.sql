-- example#Shipment
CREATE TABLE shipments (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    label TEXT,
    destination TEXT,
    state TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Queries

-- example#CreateShipment
INSERT INTO shipments (label, destination, state) VALUES (?, ?, ?) RETURNING id;

-- example#UpdateShipment
UPDATE shipments
SET label = ?, destination = ?, state = ?
WHERE id = ?;

-- example#DeleteShipment
DELETE FROM shipments WHERE id = ? RETURNING id;

-- example#GetShipment
SELECT shipments.id, shipments.label, shipments.destination, shipments.state, shipments.created_at
FROM shipments
WHERE id = ?;