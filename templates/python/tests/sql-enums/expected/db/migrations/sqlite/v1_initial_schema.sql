-- example#Task
CREATE TABLE tasks (
    id TEXT NOT NULL,
    label TEXT,
    status TEXT CHECK(status IN ('closed', 'open')),
    priority INTEGER CHECK(priority IN (2, 1)),

    PRIMARY KEY (id)
);