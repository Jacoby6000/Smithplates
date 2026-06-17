-- example#TreeNode
CREATE TABLE tree_nodes (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,
    parent_node_id UUID,

    PRIMARY KEY (id),
    FOREIGN KEY (parent_node_id) REFERENCES tree_nodes (id)
);