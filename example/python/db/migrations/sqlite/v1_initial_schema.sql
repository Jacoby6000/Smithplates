-- petstore#Store
CREATE TABLE stores (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT CHECK(length(name) <= 128),

    PRIMARY KEY (id)
);

-- petstore#Category
CREATE TABLE categories (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT CHECK(length(name) <= 128),
    store_id TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (store_id) REFERENCES stores (id)
);

-- petstore#Order
CREATE TABLE orders (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    label TEXT CHECK(length(label) <= 64),
    status TEXT CHECK(status IN ('approved', 'delivered', 'placed')),
    priority INTEGER CHECK(priority IN (3, 1, 2)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore#OrderLine
CREATE TABLE order_lines (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    order_id TEXT,
    pet_id TEXT,
    quantity INTEGER,
    unit_price_cents BIGINT,
    fulfillment TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- petstore#Owner
CREATE TABLE owners (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    full_name TEXT CHECK(length(full_name) <= 128),
    mailing_address TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore#Pet
CREATE TABLE pets (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT CHECK(length(name) <= 64),
    status TEXT CHECK(status IN ('available', 'pending', 'sold')),
    species INTEGER CHECK(species IN (3, 2, 1, 4)),
    category_id TEXT,
    owner_id TEXT,
    tag_count INTEGER,
    tags TEXT,
    featured_attribute TEXT,
    photo BLOB,
    adopted_at REAL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    FOREIGN KEY (category_id) REFERENCES categories (id),
    FOREIGN KEY (owner_id) REFERENCES owners (id)
);

-- petstore#Pet
CREATE INDEX idx_pets_status ON pets (tag_count);

-- petstore#PetProfile
CREATE TABLE pet_profiles (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    biography TEXT CHECK(length(biography) <= 256),
    pet_id TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (pet_id) REFERENCES pets (id)
);

-- petstore#PetProfile
CREATE UNIQUE INDEX uidx_pet_profiles_pet_id ON pet_profiles (pet_id);