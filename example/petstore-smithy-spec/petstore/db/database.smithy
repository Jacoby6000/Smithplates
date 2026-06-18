$version: "2.0"
namespace petstore.db

use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlCreatedTimestamp
use smithplates.codegen.sql#sqlForeignKey
use smithplates.codegen.sql#sqlIndex
use smithplates.codegen.sql#sqlJson
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#sqlUniqueIndex
use smithplates.codegen.sql#sqlUpdatedTimestamp
use smithplates.codegen.sql#sqlVarchar
use smithplates.codegen.sql#sqlDeriveDelete
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlDeriveUpdate
use smithplates.codegen.sql#sqlService
use smithy.api#required
use smithy.api#timestampFormat

@sqlTable(name: "stores")
structure Store {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    @sqlVarchar(maxLength: 128)
    name: String
}

@sqlTable(name: "categories")
structure Category {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    @sqlVarchar(maxLength: 128)
    name: String
    @sqlForeignKey(references: "petstore.db#Store")
    @required
    store_id: String
}

@sqlTable(name: "owners")
structure Owner {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    @sqlVarchar(maxLength: 128)
    full_name: String
    @required
    @sqlJson
    mailing_address: PostalAddress
    @sqlCreatedTimestamp
    created_at: Timestamp
}

@sqlTable(name: "pet_profiles")
structure PetProfile {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    @sqlVarchar(maxLength: 256)
    biography: String
    @sqlForeignKey(references: "petstore.db#Pet")
    @sqlUniqueIndex(name: "uidx_pet_profiles_pet_id")
    @required
    pet_id: String
}

@sqlTable(name: "pets")
structure Pet {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    @sqlVarchar(maxLength: 64)
    name: String
    @required
    status: PetStatus
    @required
    species: PetSpecies
    @sqlForeignKey(references: "petstore.db#Category")
    @required
    category_id: String
    @sqlForeignKey(references: "petstore.db#Owner")
    owner_id: String
    @sqlIndex(name: "idx_pets_status")
    @required
    tag_count: Integer
    @required
    @sqlJson
    tags: PetTags
    @required
    @sqlJson
    featured_attribute: PetHighlight
    photo: Blob
    @timestampFormat("epoch-seconds")
    adopted_at: Timestamp
    @sqlCreatedTimestamp
    created_at: Timestamp
    @sqlUpdatedTimestamp
    updated_at: Timestamp
}

@sqlTable(name: "orders")
structure Order {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @required
    @sqlVarchar(maxLength: 64)
    label: String
    @required
    status: OrderStatus
    @required
    priority: OrderPriority
    @sqlCreatedTimestamp
    created_at: Timestamp
    @sqlUpdatedTimestamp
    updated_at: Timestamp
}

@sqlTable(name: "order_lines")
structure OrderLine {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    @sqlForeignKey(references: "petstore.db#Order")
    @required
    order_id: String
    @required
    pet_id: String
    @required
    quantity: Integer
    @required
    unit_price_cents: Long
    @required
    @sqlJson
    fulfillment: FulfillmentState
}

@sqlDeriveInsert(targetTable: "petstore.db#Pet")
operation CreatePetRecord {
    input: DerivedStruct
    output: CreatePetRecordOutput
}

structure CreatePetRecordOutput {
    @required
    id: String
}

@sqlDeriveSelectOne(
    targetTable: "petstore.db#Pet",
    joins: [
        { table: "petstore.db#Category", tableAlias: "c" },
        { table: "petstore.db#Store", tableAlias: "s" },
        { table: "petstore.db#Owner", type: "left", tableAlias: "o" },
        { table: "petstore.db#PetProfile", type: "left", tableAlias: "pp" }
    ]
)
operation GetPetRecord {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlDeriveUpdate(targetTable: "petstore.db#Pet")
operation UpdatePetRecord {
    input: DerivedStruct
    output: UpdatePetRecordOutput
}

structure UpdatePetRecordOutput {
    @required
    updated: Boolean
}

@sqlDeriveDelete(targetTable: "petstore.db#Pet")
operation DeletePetRecord {
    input: DerivedStruct
    output: DeletePetRecordOutput
}

structure DeletePetRecordOutput {
    @required
    deleted: Boolean
}

@sqlService
service PetRepository {
    version: "1"
    operations: [CreatePetRecord, GetPetRecord, UpdatePetRecord, DeletePetRecord]
}

@sqlDeriveInsert(targetTable: "petstore.db#Category")
operation CreateCategoryRecord {
    input: DerivedStruct
    output: CreateCategoryRecordOutput
}

structure CreateCategoryRecordOutput {
    @required
    id: String
}

@sqlDeriveSelectOne(
    targetTable: "petstore.db#Category",
    joins: [{ table: "petstore.db#Store", tableAlias: "s" }]
)
operation GetCategoryRecord {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlService
service CategoryRepository {
    version: "1"
    operations: [CreateCategoryRecord, GetCategoryRecord]
}

@sqlDeriveInsert(targetTable: "petstore.db#Order")
operation CreateOrderRecord {
    input: DerivedStruct
    output: CreateOrderRecordOutput
}

structure CreateOrderRecordOutput {
    @required
    id: String
}

@sqlDeriveSelectOne(
    targetTable: "petstore.db#Order",
    joins: [{ table: "petstore.db#OrderLine", tableAlias: "ol" }]
)
operation GetOrderRecord {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlService
service OrderRepository {
    version: "1"
    operations: [CreateOrderRecord, GetOrderRecord]
}
