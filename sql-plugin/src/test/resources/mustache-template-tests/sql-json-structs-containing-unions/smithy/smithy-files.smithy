$version: "2.0"
namespace example

use jacoby6000.codegen.sql#sqlAutoUuid
use jacoby6000.codegen.sql#sqlCreatedTimestamp
use jacoby6000.codegen.sql#sqlPrimaryKey
use jacoby6000.codegen.sql#sqlTable
use jacoby6000.codegen.sql#sqlJson
use jacoby6000.codegen.sql#DerivedStruct
use jacoby6000.codegen.sql#sqlDeriveDelete
use jacoby6000.codegen.sql#sqlDeriveInsert
use jacoby6000.codegen.sql#sqlDeriveSelectOne
use jacoby6000.codegen.sql#sqlDeriveUpdate
use jacoby6000.codegen.sql#sqlService
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
