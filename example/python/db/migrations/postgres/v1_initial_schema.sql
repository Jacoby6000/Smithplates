-- petstore.db#OrderStatus
CREATE TYPE petstore_db_orderstatus AS ENUM ('approved', 'delivered', 'placed');

-- petstore.db#PetStatus
CREATE TYPE petstore_db_petstatus AS ENUM ('available', 'pending', 'sold');

-- petstore.db#Store
CREATE TABLE stores (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(128),

    PRIMARY KEY (id)
);

-- petstore.db#Category
CREATE TABLE categories (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(128),
    store_id UUID,

    PRIMARY KEY (id),
    FOREIGN KEY (store_id) REFERENCES stores (id)
);

-- petstore.db#Order
CREATE TABLE orders (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label VARCHAR(64),
    status petstore_db_orderstatus,
    priority INTEGER CHECK(priority IN (3, 1, 2)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore.db#OrderLine
CREATE TABLE order_lines (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id UUID,
    pet_id TEXT,
    quantity INTEGER,
    unit_price_cents BIGINT,
    fulfillment JSONB,

    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- petstore.db#Owner
CREATE TABLE owners (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    full_name VARCHAR(128),
    mailing_address JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore.db#Pet
CREATE TABLE pets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(64),
    status petstore_db_petstatus,
    species INTEGER CHECK(species IN (3, 2, 1, 4)),
    category_id UUID,
    owner_id UUID,
    tag_count INTEGER,
    tags JSONB,
    featured_attribute JSONB,
    photo BYTEA,
    adopted_at DECIMAL(13, 3),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    FOREIGN KEY (category_id) REFERENCES categories (id),
    FOREIGN KEY (owner_id) REFERENCES owners (id)
);

-- petstore.db#Pet
CREATE INDEX idx_pets_status ON pets (tag_count);

-- petstore.db#PetProfile
CREATE TABLE pet_profiles (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    biography VARCHAR(256),
    pet_id UUID,

    PRIMARY KEY (id),
    FOREIGN KEY (pet_id) REFERENCES pets (id)
);

-- petstore.db#PetProfile
CREATE UNIQUE INDEX uidx_pet_profiles_pet_id ON pet_profiles (pet_id);