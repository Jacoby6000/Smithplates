-- example#Bar
CREATE TABLE bars (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    name TEXT,

    PRIMARY KEY (id)
);

-- example#Profile
CREATE TABLE profiles (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    display_name TEXT,
    bar_id UUID,

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
WHERE profiles.id = $1;