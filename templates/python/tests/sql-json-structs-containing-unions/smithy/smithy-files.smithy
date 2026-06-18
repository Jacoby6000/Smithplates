$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlCreatedTimestamp
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#sqlJson
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlDeriveDelete
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlDeriveUpdate
use smithplates.codegen.sql#sqlService
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
    output: DerivedStruct
}

@sqlDeriveSelectOne(targetTable: "example#Shipment")
operation GetShipment {
    input: DerivedStruct
    output: Shipment
    errors: [ShipmentNotFound]
}

structure DeleteShipmentOutput {
    @required
    deleted: Boolean
}

@sqlDeriveUpdate(targetTable: "example#Shipment")
operation UpdateShipment {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlDeriveDelete(targetTable: "example#Shipment")
operation DeleteShipment {
    input: DerivedStruct
    output: DeleteShipmentOutput
}

@sqlService
service ShipmentRepository {
    version: "1"
    operations: [CreateShipment, GetShipment, UpdateShipment, DeleteShipment]
}
