-- example#Widget
CREATE TABLE widgets (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    foo TEXT,
    bar BIGINT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Queries

-- example#CreateWidget
INSERT INTO widgets (foo, bar) VALUES (?, ?) RETURNING id;

-- example#UpdateWidget
UPDATE widgets
SET foo = ?, bar = ?, updated_at = CURRENT_TIMESTAMP
WHERE id = ? RETURNING updated_at;

-- example#DeleteWidget
DELETE FROM widgets WHERE id = ? RETURNING id;

-- example#GetWidget
SELECT widgets.id, widgets.foo, widgets.bar, widgets.created_at, widgets.updated_at
FROM widgets
WHERE id = ?;