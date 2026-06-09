-- example#Bookmark
CREATE TABLE bookmarks (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    title TEXT,

    PRIMARY KEY (id)
);

-- Queries

-- example#CreateBookmark
INSERT INTO bookmarks (title) VALUES (?) RETURNING id;