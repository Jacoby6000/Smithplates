-- example#TaskStatus
CREATE TYPE example_taskstatus AS ENUM ('closed', 'open');

-- example#Task
CREATE TABLE tasks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,
    status example_taskstatus,
    priority INTEGER CHECK(priority IN (2, 1)),

    PRIMARY KEY (id)
);