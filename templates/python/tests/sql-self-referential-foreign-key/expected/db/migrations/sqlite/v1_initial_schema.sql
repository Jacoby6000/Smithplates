-- example#TreeNode
CREATE TABLE tree_nodes (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    label TEXT,
    parent_node_id TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (parent_node_id) REFERENCES tree_nodes (id)
);