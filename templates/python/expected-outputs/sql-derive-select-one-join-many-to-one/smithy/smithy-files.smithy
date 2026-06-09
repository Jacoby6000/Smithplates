$version: "2.0"
namespace example

use stache.codegen.sql#sqlAutoUuid
use stache.codegen.sql#sqlDeriveSelectOne
use stache.codegen.sql#sqlForeignKey
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlService
use smithy.api#required

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
    @required
    category_id: String
}

@sqlDeriveSelectOne(
    targetTable: "example#Widget",
    joins: [{ table: "example#Category", tableAlias: "c" }]
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
