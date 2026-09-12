$version: "2.0"
namespace example

use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#sqlUuid

@sqlUuid
string TenantId

@sqlUuid
string ExternalId

@sqlTable(name: "accounts")
structure Account {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    tenant_id: TenantId
    external_id: ExternalId
}

@sqlDeriveInsert(targetTable: "example#Account")
operation CreateAccount {
    input: DerivedStruct
    output: String
}

@sqlService
service AccountRepository {
    version: "1"
    operations: [CreateAccount]
}
