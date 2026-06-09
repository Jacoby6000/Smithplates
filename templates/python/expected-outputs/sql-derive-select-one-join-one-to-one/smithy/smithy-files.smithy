$version: "2.0"
namespace example

use stache.codegen.sql#sqlAutoUuid
use stache.codegen.sql#sqlDeriveSelectOne
use stache.codegen.sql#sqlForeignKey
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable
use stache.codegen.sql#sqlUniqueIndex
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlService
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
