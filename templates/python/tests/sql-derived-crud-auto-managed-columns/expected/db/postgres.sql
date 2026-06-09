-- example#Widget
CREATE TABLE widgets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    foo TEXT,
    bar BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Queries

-- example#CreateWidget
INSERT INTO widgets (foo, bar) VALUES ($1, $2) RETURNING id;

-- example#UpdateWidget
UPDATE widgets
SET foo = $1, bar = $2, updated_at = CURRENT_TIMESTAMP
WHERE id = $3 RETURNING updated_at;

-- example#DeleteWidget
DELETE FROM widgets WHERE id = $1 RETURNING id;

-- example#GetWidget
SELECT widgets.id, widgets.foo, widgets.bar, widgets.created_at, widgets.updated_at
FROM widgets
WHERE id = $1;