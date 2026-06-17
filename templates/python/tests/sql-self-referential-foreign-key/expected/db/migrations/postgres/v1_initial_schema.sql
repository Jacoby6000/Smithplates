-- example#TreeNode
CREATE TABLE tree_nodes (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,
    parent_node_id UUID /* FK -> tree_nodes (id) */,

    PRIMARY KEY (id)
);

-- example#TreeNode
ALTER TABLE tree_nodes
    ADD CONSTRAINT fk_tree_nodes_parent_node_id
    FOREIGN KEY (parent_node_id) REFERENCES tree_nodes (id);