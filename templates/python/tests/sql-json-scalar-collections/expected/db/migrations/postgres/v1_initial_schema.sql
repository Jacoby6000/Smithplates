-- example#Record
CREATE TABLE records (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    instants JSONB NOT NULL,
    instant_map JSONB NOT NULL,
    amounts JSONB NOT NULL,
    amount_map JSONB NOT NULL,
    payloads JSONB NOT NULL,
    payload_map JSONB NOT NULL,
    instant_groups JSONB NOT NULL,
    nested_amounts JSONB NOT NULL,
    optional_instants JSONB,

    PRIMARY KEY (id)
);