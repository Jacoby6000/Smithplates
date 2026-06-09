-- example#Bookmark
CREATE TABLE bookmarks (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    title TEXT,

    PRIMARY KEY (id)
);

-- Queries

-- example#GetBookmark
SELECT bookmarks.id, bookmarks.title
FROM bookmarks
WHERE id = $1;