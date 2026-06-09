$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlForeignKey
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#sqlUniqueIndex
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlService
use smithy.api#required

@sqlTable(name: "bars")
structure Bar {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    name: String
}

@sqlTable(name: "profiles")
structure Profile {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    display_name: String
    @sqlForeignKey(references: "example#Bar")
    @sqlUniqueIndex(name: "uidx_profiles_bar_id")
    @required
    bar_id: String
}

@sqlDeriveSelectOne(
    targetTable: "example#Profile",
    joins: [{ table: "example#Bar", tableAlias: "b" }]
)
operation GetProfile {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlService
service ProfileRepository {
    version: "1"
    operations: [GetProfile]
}
