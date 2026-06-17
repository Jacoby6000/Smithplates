-- example#Department
CREATE TABLE departments (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name TEXT,

    PRIMARY KEY (id)
);

-- example#Category
CREATE TABLE categories (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name TEXT,
    department_id UUID NOT NULL /* FK -> departments (id) */,

    PRIMARY KEY (id)
);

-- example#Widget
CREATE TABLE widgets (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title TEXT,
    category_id UUID NOT NULL /* FK -> categories (id) */,

    PRIMARY KEY (id)
);

-- example#Category
ALTER TABLE categories
    ADD CONSTRAINT fk_categories_department_id
    FOREIGN KEY (department_id) REFERENCES departments (id);

-- example#Widget
ALTER TABLE widgets
    ADD CONSTRAINT fk_widgets_category_id
    FOREIGN KEY (category_id) REFERENCES categories (id);