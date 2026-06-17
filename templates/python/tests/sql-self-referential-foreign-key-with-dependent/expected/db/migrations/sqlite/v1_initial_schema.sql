-- example#Category
CREATE TABLE categories (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT,
    parent_category_id TEXT /* FK -> categories (id) */,

    PRIMARY KEY (id),
    FOREIGN KEY (parent_category_id) REFERENCES categories (id)
);

-- example#CategoryItem
CREATE TABLE category_items (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    category_id TEXT /* FK -> categories (id) */,
    label TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (category_id) REFERENCES categories (id)
);