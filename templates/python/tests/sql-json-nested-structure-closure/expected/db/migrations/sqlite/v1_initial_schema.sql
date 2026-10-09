-- example#Customer
CREATE TABLE customers (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT NOT NULL,
    contact TEXT NOT NULL,
    labels TEXT NOT NULL,
    contacts TEXT NOT NULL,
    contact_map TEXT NOT NULL,
    alternate_labels TEXT,
    collection_only TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);