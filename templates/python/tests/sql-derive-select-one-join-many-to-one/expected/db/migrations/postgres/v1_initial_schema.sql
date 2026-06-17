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
    category_id UUID NOT NULL /* FK -> categories (id) */,

    PRIMARY KEY (id)
);

-- example#Widget
ALTER TABLE widgets
    ADD CONSTRAINT fk_widgets_category_id
    FOREIGN KEY (category_id) REFERENCES categories (id);