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

structure Leaf { @required text: String }
map Leaves { key: String, value: Leaf }
structure Branch { @required count: Integer }
union Choice { branch: Branch, text: String }
list Choices { member: Choice }
list Branches { member: Branch }
map BranchGroups { key: String, value: Branches }

@sqlTable(name: "records")
structure Record {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    @sqlJson
    leaves: Leaves
    @required
    @sqlJson
    choices: Choices
    @sqlJson
    groups: BranchGroups
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
