-- example#Record
CREATE TABLE records (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    leaves JSONB NOT NULL,
    choices JSONB NOT NULL,
    singleton_choices JSONB NOT NULL,
    groups JSONB,

    PRIMARY KEY (id)
);