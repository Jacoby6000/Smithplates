-- example#Record
CREATE TABLE records (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    instants TEXT NOT NULL,
    instant_map TEXT NOT NULL,
    amounts TEXT NOT NULL,
    amount_map TEXT NOT NULL,
    payloads TEXT NOT NULL,
    payload_map TEXT NOT NULL,
    instant_groups TEXT NOT NULL,
    nested_amounts TEXT NOT NULL,
    optional_instants TEXT,

    PRIMARY KEY (id)
);