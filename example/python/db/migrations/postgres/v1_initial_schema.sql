-- petstore.db#OrderStatus
CREATE TYPE petstore_db_orderstatus AS ENUM ('approved', 'delivered', 'placed');

-- petstore.db#PetStatus
CREATE TYPE petstore_db_petstatus AS ENUM ('available', 'pending', 'sold');

-- petstore.db#Order
CREATE TABLE orders (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label VARCHAR(64) NOT NULL,
    status petstore_db_orderstatus NOT NULL,
    priority INTEGER NOT NULL CHECK(priority IN (3, 1, 2)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore.db#Owner
CREATE TABLE owners (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    full_name VARCHAR(128) NOT NULL,
    mailing_address JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore.db#Store
CREATE TABLE stores (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,

    PRIMARY KEY (id)
);

-- petstore.db#Category
CREATE TABLE categories (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(128) NOT NULL,
    store_id UUID NOT NULL /* FK -> stores (id) */,

    PRIMARY KEY (id)
);

-- petstore.db#OrderLine
CREATE TABLE order_lines (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id UUID NOT NULL /* FK -> orders (id) */,
    pet_id TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    fulfillment JSONB NOT NULL
);

-- petstore.db#Pet
CREATE TABLE pets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name VARCHAR(64) NOT NULL,
    status petstore_db_petstatus NOT NULL,
    species INTEGER NOT NULL CHECK(species IN (3, 2, 1, 4)),
    category_id UUID NOT NULL /* FK -> categories (id) */,
    owner_id UUID /* FK -> owners (id) */,
    tag_count INTEGER NOT NULL,
    tags JSONB NOT NULL,
    featured_attribute JSONB NOT NULL,
    photo BYTEA,
    adopted_at DECIMAL(13, 3),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- petstore.db#Pet
CREATE INDEX idx_pets_status ON pets (tag_count);

-- petstore.db#PetProfile
CREATE TABLE pet_profiles (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    biography VARCHAR(256) NOT NULL,
    pet_id UUID NOT NULL /* FK -> pets (id) */,

    PRIMARY KEY (id)
);

-- petstore.db#PetProfile
CREATE UNIQUE INDEX uidx_pet_profiles_pet_id ON pet_profiles (pet_id);

-- petstore.db#Category
ALTER TABLE categories
    ADD CONSTRAINT fk_categories_store_id
    FOREIGN KEY (store_id) REFERENCES stores (id);

-- petstore.db#OrderLine
ALTER TABLE order_lines
    ADD CONSTRAINT fk_order_lines_order_id
    FOREIGN KEY (order_id) REFERENCES orders (id);

-- petstore.db#PetProfile
ALTER TABLE pet_profiles
    ADD CONSTRAINT fk_pet_profiles_pet_id
    FOREIGN KEY (pet_id) REFERENCES pets (id);

-- petstore.db#Pet
ALTER TABLE pets
    ADD CONSTRAINT fk_pets_category_id
    FOREIGN KEY (category_id) REFERENCES categories (id);

-- petstore.db#Pet
ALTER TABLE pets
    ADD CONSTRAINT fk_pets_owner_id
    FOREIGN KEY (owner_id) REFERENCES owners (id);