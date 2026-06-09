$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlForeignKey
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlService
use smithy.api#required

@sqlTable(name: "departments")
structure Department {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    name: String
}

@sqlTable(name: "categories")
structure Category {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    name: String
    @sqlForeignKey(references: "example#Department")
    @required
    department_id: String
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
    joins: [
        { table: "example#Category", tableAlias: "c" },
        { table: "example#Department", tableAlias: "d" }
    ]
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
