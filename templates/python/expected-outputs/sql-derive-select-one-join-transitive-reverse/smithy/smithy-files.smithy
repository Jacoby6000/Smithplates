$version: "2.0"
namespace example

use stache.codegen.sql#sqlAutoUuid
use stache.codegen.sql#sqlDeriveSelectOne
use stache.codegen.sql#sqlForeignKey
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlService

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
    department_id: String
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
    targetTable: "example#Department",
    joins: [
        { table: "example#Category", tableAlias: "c" },
        { table: "example#Widget", tableAlias: "w" }
    ]
)
operation GetDepartment {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlService
service DepartmentRepository {
    version: "1"
    operations: [GetDepartment]
}
