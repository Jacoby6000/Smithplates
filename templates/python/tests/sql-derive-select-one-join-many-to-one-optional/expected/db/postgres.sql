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

-- Queries

-- example#GetWidget
SELECT widgets.id, widgets.title, widgets.category_id, c.id AS c_id, c.name AS c_name
FROM widgets AS widgets
LEFT JOIN categories AS c ON widgets.category_id = c.id
WHERE widgets.id = $1;