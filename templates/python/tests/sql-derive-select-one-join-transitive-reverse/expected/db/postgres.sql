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
    department_id UUID,

    PRIMARY KEY (id),
    FOREIGN KEY (department_id) REFERENCES departments (id)
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

-- example#GetDepartment
SELECT departments.id, departments.name, c.id AS c_id, c.name AS c_name, c.department_id AS c_department_id, w.id AS w_id, w.title AS w_title, w.category_id AS w_category_id
FROM departments AS departments
LEFT JOIN categories AS c ON departments.id = c.department_id
LEFT JOIN widgets AS w ON c.id = w.category_id
WHERE departments.id = $1;