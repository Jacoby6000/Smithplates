-- example#Record
CREATE TABLE records (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    metadata JSONB NOT NULL,

    PRIMARY KEY (id)
);