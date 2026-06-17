-- example#Shipment
CREATE TABLE shipments (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT NOT NULL,
    destination JSONB NOT NULL,
    state JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);