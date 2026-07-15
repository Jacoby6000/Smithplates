$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlCreatedTimestamp
use smithplates.codegen.sql#sqlDeriveDelete
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlDeriveUpdate
use smithplates.codegen.sql#sqlJson
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlService
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#DerivedStruct
use smithy.api#error
use smithy.api#required

/// Deepest nested structure: only reachable transitively through the @sqlJson
/// column. Must still get _dump_/_map_to_ helpers and must surface the
/// Timestamp mapper (recorded_at) via the JSON structure closure.
structure GeoCoordinates {
    @required
    lat: Float
    @required
    lng: Float
    @required
    recorded_at: Timestamp
}

/// Intermediate nested structure: referenced by ContactInfo, not a @sqlJson
/// column itself. Must be emitted with dump/map helpers via the closure.
structure PostalAddress {
    @required
    street: String
    @required
    city: String
    @required
    coords: GeoCoordinates
}

/// Direct @sqlJson column structure. Its dump helper references PostalAddress's
/// dump helper, which references GeoCoordinates's dump helper.
structure ContactInfo {
    @required
    email: String
    @required
    address: PostalAddress
}

@sqlTable(name: "customers")
structure Customer {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    name: String
    @required
    @sqlJson
    contact: ContactInfo
    @sqlCreatedTimestamp
    created_at: Timestamp
}

@error("client")
structure CustomerNotFound {
    @required
    message: String
}

@sqlDeriveInsert(targetTable: "example#Customer")
operation CreateCustomer {
    input: DerivedStruct
    output: String
}

@sqlDeriveSelectOne(targetTable: "example#Customer")
operation GetCustomer {
    input: DerivedStruct
    output: Customer
    errors: [CustomerNotFound]
}

@sqlDeriveUpdate(targetTable: "example#Customer")
operation UpdateCustomer {
    input: DerivedStruct
    output: Boolean
}

@sqlDeriveDelete(targetTable: "example#Customer")
operation DeleteCustomer {
    input: DerivedStruct
    output: Boolean
}

@sqlService
service CustomerRepository {
    version: "1"
    operations: [CreateCustomer, GetCustomer, UpdateCustomer, DeleteCustomer]
}
