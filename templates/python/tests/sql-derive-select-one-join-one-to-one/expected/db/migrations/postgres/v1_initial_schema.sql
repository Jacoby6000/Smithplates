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