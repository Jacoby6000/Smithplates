$version: "2.0"
namespace example

use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlForeignKey
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable

@sqlTable(name: "categories")
structure Category {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String

    name: String

    @sqlForeignKey(references: "example#Category")
    parent_category_id: String
}

@sqlTable(name: "category_items")
structure CategoryItem {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String

    @sqlForeignKey(references: "example#Category")
    category_id: String

    label: String
}

@sqlDeriveInsert(targetTable: "example#Category")
operation CreateCategory {
    input: DerivedStruct
    output: String
}

@sqlDeriveSelectOne(targetTable: "example#Category")
operation GetCategory {
    input: DerivedStruct
    output: Category
}

@sqlService
service CategoryRepository {
    version: "1"
    operations: [CreateCategory, GetCategory]
}
