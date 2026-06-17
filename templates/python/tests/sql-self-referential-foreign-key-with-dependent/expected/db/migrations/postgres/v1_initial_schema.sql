-- example#Category
CREATE TABLE categories (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name TEXT,
    parent_category_id UUID /* FK -> categories (id) */,

    PRIMARY KEY (id)
);

-- example#CategoryItem
CREATE TABLE category_items (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    category_id UUID /* FK -> categories (id) */,
    label TEXT,

    PRIMARY KEY (id)
);

-- example#Category
ALTER TABLE categories
    ADD CONSTRAINT fk_categories_parent_category_id
    FOREIGN KEY (parent_category_id) REFERENCES categories (id);

-- example#CategoryItem
ALTER TABLE category_items
    ADD CONSTRAINT fk_category_items_category_id
    FOREIGN KEY (category_id) REFERENCES categories (id);