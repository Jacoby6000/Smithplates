-- petstore.db#Order
CREATE TABLE orders (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    label TEXT NOT NULL CHECK(length(label) <= 64),
    status TEXT NOT NULL CHECK(status IN ('approved', 'delivered', 'placed')),
    priority INTEGER NOT NULL CHECK(priority IN (3, 1, 2)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore.db#Owner
CREATE TABLE owners (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    full_name TEXT NOT NULL CHECK(length(full_name) <= 128),
    mailing_address TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore.db#Store
CREATE TABLE stores (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT NOT NULL CHECK(length(name) <= 128),

    PRIMARY KEY (id)
);

-- petstore.db#Category
CREATE TABLE categories (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT NOT NULL CHECK(length(name) <= 128),
    store_id TEXT NOT NULL /* FK -> stores (id) */,

    PRIMARY KEY (id),
    FOREIGN KEY (store_id) REFERENCES stores (id)
);

-- petstore.db#OrderLine
CREATE TABLE order_lines (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    order_id TEXT NOT NULL /* FK -> orders (id) */,
    pet_id TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    fulfillment TEXT NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- petstore.db#Pet
CREATE TABLE pets (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT NOT NULL CHECK(length(name) <= 64),
    status TEXT NOT NULL CHECK(status IN ('available', 'pending', 'sold')),
    species INTEGER NOT NULL CHECK(species IN (3, 2, 1, 4)),
    category_id TEXT NOT NULL /* FK -> categories (id) */,
    owner_id TEXT /* FK -> owners (id) */,
    tag_count INTEGER NOT NULL,
    tags TEXT NOT NULL,
    featured_attribute TEXT NOT NULL,
    photo BLOB,
    adopted_at REAL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    FOREIGN KEY (category_id) REFERENCES categories (id),
    FOREIGN KEY (owner_id) REFERENCES owners (id)
);

-- petstore.db#Pet
CREATE INDEX idx_pets_status ON pets (tag_count);

-- petstore.db#PetProfile
CREATE TABLE pet_profiles (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    biography TEXT NOT NULL CHECK(length(biography) <= 256),
    pet_id TEXT NOT NULL /* FK -> pets (id) */,

    PRIMARY KEY (id),
    FOREIGN KEY (pet_id) REFERENCES pets (id)
);

-- petstore.db#PetProfile
CREATE UNIQUE INDEX uidx_pet_profiles_pet_id ON pet_profiles (pet_id);