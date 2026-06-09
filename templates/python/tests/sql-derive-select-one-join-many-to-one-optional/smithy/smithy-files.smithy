$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlForeignKey
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlService

@sqlTable(name: "categories")
structure Category {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    name: String
}

@sqlTable(name: "widgets")
structure Widget {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    title: String
    @sqlForeignKey(references: "example#Category")
    category_id: String
}

@sqlDeriveSelectOne(
    targetTable: "example#Widget",
    joins: [{ table: "example#Category", type: "left", tableAlias: "c" }]
)
operation GetWidget {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlService
service WidgetRepository {
    version: "1"
    operations: [GetWidget]
}
