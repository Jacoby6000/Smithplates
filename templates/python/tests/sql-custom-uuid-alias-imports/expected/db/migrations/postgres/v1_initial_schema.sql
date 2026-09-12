-- example#Account
CREATE TABLE accounts (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id UUID,
    external_id UUID,

    PRIMARY KEY (id)
);