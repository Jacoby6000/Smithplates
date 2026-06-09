-- example#Bookmark
CREATE TABLE bookmarks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title TEXT,

    PRIMARY KEY (id)
);

-- Queries

-- example#UpdateBookmark
UPDATE bookmarks
SET title = $1
WHERE id = $2;