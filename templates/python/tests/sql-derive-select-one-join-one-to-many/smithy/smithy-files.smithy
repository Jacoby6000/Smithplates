$version: "2.0"
namespace example

use smithy.api#required

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlForeignKey
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlService

@sqlTable(name: "orders")
structure Order {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    label: String
}

@sqlTable(name: "order_lines")
structure OrderLine {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @sqlForeignKey(references: "example#Order")
    order_id: String
    sku: String
}

@sqlDeriveInsert(targetTable: "example#Order")
operation CreateOrder {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlDeriveSelectOne(
    targetTable: "example#Order",
    joins: [{ table: "example#OrderLine", tableAlias: "ol" }]
)
operation GetOrder {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlService
service OrderRepository {
    version: "1"
    operations: [CreateOrder, GetOrder]
}
