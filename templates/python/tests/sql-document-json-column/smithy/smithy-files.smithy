$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#DerivedStruct

/// Golden case: Smithy Document columns use generated JSON bind/read helpers.
@sqlTable(name: "records")
structure Record {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String

    @required
    metadata: Document
}

@sqlDeriveInsert(targetTable: "example#Record")
operation InsertRecord {
    input: DerivedStruct
    output: String
}

@sqlDeriveSelectOne(targetTable: "example#Record")
operation GetRecordById {
    input: DerivedStruct
    output: Record
}

@sqlService
service RecordRepository {
    version: "1"
    operations: [InsertRecord, GetRecordById]
}
