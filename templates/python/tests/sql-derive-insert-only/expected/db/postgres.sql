-- example#Bookmark
CREATE TABLE bookmarks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title TEXT,

    PRIMARY KEY (id)
);

-- Queries

-- example#CreateBookmark
INSERT INTO bookmarks (title) VALUES ($1) RETURNING id;