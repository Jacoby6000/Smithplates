-- example#Category
CREATE TABLE categories (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name TEXT,

    PRIMARY KEY (id)
);

-- example#Widget
CREATE TABLE widgets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title TEXT,
    category_id UUID,

    PRIMARY KEY (id),
    FOREIGN KEY (category_id) REFERENCES categories (id)
);