-- example#Shipment
CREATE TABLE shipments (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,
    destination JSONB,
    state JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);