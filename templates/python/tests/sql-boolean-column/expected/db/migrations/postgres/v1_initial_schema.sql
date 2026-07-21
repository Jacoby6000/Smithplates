-- example#Flag
CREATE TABLE flags (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,
    enabled BOOLEAN,

    PRIMARY KEY (id)
);