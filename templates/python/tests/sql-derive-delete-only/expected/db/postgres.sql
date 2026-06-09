-- example#Bookmark
CREATE TABLE bookmarks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title TEXT,

    PRIMARY KEY (id)
);

-- Queries

-- example#DeleteBookmark
DELETE FROM bookmarks WHERE id = $1 RETURNING id;