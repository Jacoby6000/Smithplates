$version: "2.0"

namespace example

use smithy.api#required

use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlForeignKey
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable

@sqlTable(name: "tree_nodes")
structure TreeNode {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String

    label: String

    @sqlForeignKey(references: "example#TreeNode")
    parent_node_id: String
}

@sqlDeriveInsert(targetTable: "example#TreeNode")
operation CreateTreeNode {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlDeriveSelectOne(targetTable: "example#TreeNode")
operation GetTreeNode {
    input: DerivedStruct
    output: TreeNode
}

@sqlService
service TreeNodeRepository {
    version: "1"
    operations: [CreateTreeNode, GetTreeNode]
}
