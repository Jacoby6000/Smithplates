-- example#Customer
CREATE TABLE customers (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    contact JSONB NOT NULL,
    labels JSONB NOT NULL,
    contacts JSONB NOT NULL,
    contact_map JSONB NOT NULL,
    alternate_labels JSONB,
    collection_only JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);