$version: "2.0"
namespace example

use stache.codegen.sql#sqlAutoUuid
use stache.codegen.sql#sqlCreatedTimestamp
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable
use stache.codegen.sql#sqlJson
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlDeriveDelete
use stache.codegen.sql#sqlDeriveInsert
use stache.codegen.sql#sqlDeriveSelectOne
use stache.codegen.sql#sqlDeriveUpdate
use stache.codegen.sql#sqlService
use smithy.api#error
use smithy.api#required

structure PostalAddress {
    @required
    street: String
    @required
    city: String
}

union DeliveryState {
    pending: String
    delivered: Timestamp
}

@sqlTable(name: "shipments")
structure Shipment {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    label: String
    @required
    @sqlJson
    destination: PostalAddress
    @required
    @sqlJson
    state: DeliveryState
    @sqlCreatedTimestamp
    created_at: Timestamp
}

@error("client")
structure ShipmentNotFound {
    @required
    message: String
}

@sqlDeriveInsert(targetTable: "example#Shipment")
operation CreateShipment {
    input: DerivedStruct
    output: String
}

@sqlDeriveSelectOne(targetTable: "example#Shipment")
operation GetShipment {
    input: DerivedStruct
    output: Shipment
    errors: [ShipmentNotFound]
}

@sqlDeriveUpdate(targetTable: "example#Shipment")
operation UpdateShipment {
    input: DerivedStruct
    output: Boolean
}

@sqlDeriveDelete(targetTable: "example#Shipment")
operation DeleteShipment {
    input: DerivedStruct
    output: Boolean
}

@sqlService
service ShipmentRepository {
    version: "1"
    operations: [CreateShipment, GetShipment, UpdateShipment, DeleteShipment]
}
