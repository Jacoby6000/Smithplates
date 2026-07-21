$version: "2.0"
namespace example

use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlDeriveUpdate
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable

@sqlTable(name: "flags")
structure Flag {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String

    label: String

    enabled: Boolean
}

@sqlDeriveInsert(targetTable: "example#Flag")
operation CreateFlag {
    input: DerivedStruct
    output: String
}

@sqlDeriveSelectOne(targetTable: "example#Flag")
operation GetFlag {
    input: DerivedStruct
    output: Flag
}

@sqlDeriveUpdate(targetTable: "example#Flag")
operation UpdateFlag {
    input: DerivedStruct
    output: Boolean
}

@sqlService
service FlagRepository {
    version: "1"
    operations: [CreateFlag, GetFlag, UpdateFlag]
}
