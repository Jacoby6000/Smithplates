-- example#Department
CREATE TABLE departments (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT,

    PRIMARY KEY (id)
);

-- example#Category
CREATE TABLE categories (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT,
    department_id TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (department_id) REFERENCES departments (id)
);

-- example#Widget
CREATE TABLE widgets (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    title TEXT,
    category_id TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (category_id) REFERENCES categories (id)
);

-- Queries

-- example#GetWidget
SELECT widgets.id, widgets.title, widgets.category_id, c.id AS c_id, c.name AS c_name, c.department_id AS c_department_id, d.id AS d_id, d.name AS d_name
FROM widgets AS widgets
INNER JOIN categories AS c ON widgets.category_id = c.id
INNER JOIN departments AS d ON c.department_id = d.id
WHERE widgets.id = ?;