$version: "2.0"
namespace example

use stache.codegen.sql#sqlAutoUuid
use stache.codegen.sql#sqlDeriveInsert
use stache.codegen.sql#sqlDeriveSelectOne
use stache.codegen.sql#sqlForeignKey
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlService

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
    output: String
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
