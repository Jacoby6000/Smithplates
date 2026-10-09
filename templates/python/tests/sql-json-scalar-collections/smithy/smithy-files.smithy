$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveDelete
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlDeriveUpdate
use smithplates.codegen.sql#sqlJson
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#DerivedStruct

list Instants { member: Timestamp }
map InstantMap { key: String, value: Timestamp }
list Amounts { member: BigDecimal }
map AmountMap { key: String, value: BigDecimal }
list Payloads { member: Blob }
map PayloadMap { key: String, value: Blob }
list InstantGroups { member: Instants }
map NestedAmounts { key: String, value: AmountMap }

@sqlTable(name: "records")
structure Record {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    @sqlJson
    instants: Instants
    @required
    @sqlJson
    instant_map: InstantMap
    @required
    @sqlJson
    amounts: Amounts
    @required
    @sqlJson
    amount_map: AmountMap
    @required
    @sqlJson
    payloads: Payloads
    @required
    @sqlJson
    payload_map: PayloadMap
    @required
    @sqlJson
    instant_groups: InstantGroups
    @required
    @sqlJson
    nested_amounts: NestedAmounts
    @sqlJson
    optional_instants: Instants
}

@sqlDeriveInsert(targetTable: "example#Record")
operation CreateRecord { input: DerivedStruct, output: String }
@sqlDeriveSelectOne(targetTable: "example#Record")
operation GetRecord { input: DerivedStruct, output: Record }
@sqlDeriveUpdate(targetTable: "example#Record")
operation UpdateRecord { input: DerivedStruct, output: Boolean }
@sqlDeriveDelete(targetTable: "example#Record")
operation DeleteRecord { input: DerivedStruct, output: Boolean }
@sqlService
service RecordRepository {
    version: "1"
    operations: [CreateRecord, GetRecord, UpdateRecord, DeleteRecord]
}
