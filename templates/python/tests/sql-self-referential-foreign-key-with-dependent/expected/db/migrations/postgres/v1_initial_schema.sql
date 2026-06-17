-- example#Category
CREATE TABLE categories (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name TEXT,
    parent_category_id UUID,

    PRIMARY KEY (id),
    FOREIGN KEY (parent_category_id) REFERENCES categories (id)
);

-- example#CategoryItem
CREATE TABLE category_items (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    category_id UUID,
    label TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (category_id) REFERENCES categories (id)
);