-- example#Bar
CREATE TABLE bars (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    name TEXT,

    PRIMARY KEY (id)
);

-- example#Profile
CREATE TABLE profiles (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    display_name TEXT,
    bar_id TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (bar_id) REFERENCES bars (id)
);

-- example#Profile
CREATE UNIQUE INDEX uidx_profiles_bar_id ON profiles (bar_id);

-- Queries

-- example#GetProfile
SELECT profiles.id, profiles.display_name, profiles.bar_id, b.id AS b_id, b.name AS b_name
FROM profiles AS profiles
INNER JOIN bars AS b ON profiles.bar_id = b.id
WHERE profiles.id = ?;