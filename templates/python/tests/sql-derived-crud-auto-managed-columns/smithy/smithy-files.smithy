$version: "2.0"
namespace example

use smithplates.codegen.sql#sqlAutoUuid
use smithplates.codegen.sql#sqlCreatedTimestamp
use smithplates.codegen.sql#sqlPrimaryKey
use smithplates.codegen.sql#sqlTable
use smithplates.codegen.sql#sqlUpdatedTimestamp
use smithplates.codegen.sql#DerivedStruct
use smithplates.codegen.sql#sqlDeriveDelete
use smithplates.codegen.sql#sqlDeriveInsert
use smithplates.codegen.sql#sqlDeriveSelectOne
use smithplates.codegen.sql#sqlDeriveUpdate
use smithplates.codegen.sql#sqlService
use smithy.api#error
use smithy.api#required

@sqlTable(name: "widgets")
structure Widget {
    @sqlPrimaryKey
    @sqlAutoUuid
    id: String
    foo: String
    bar: Long
    @sqlCreatedTimestamp
    created_at: Timestamp
    @sqlUpdatedTimestamp
    updated_at: Timestamp
}

@error("client")
structure WidgetNotFound {
    @required
    message: String
}

@sqlDeriveInsert(targetTable: "example#Widget")
operation CreateWidget {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlDeriveSelectOne(targetTable: "example#Widget")
operation GetWidget {
    input: DerivedStruct
    output: Widget
    errors: [WidgetNotFound]
}

structure DeleteWidgetOutput {
    @required
    deleted: Boolean
}

@sqlDeriveUpdate(targetTable: "example#Widget")
operation UpdateWidget {
    input: DerivedStruct
    output: DerivedStruct
}

@sqlDeriveDelete(targetTable: "example#Widget")
operation DeleteWidget {
    input: DerivedStruct
    output: DeleteWidgetOutput
}

@sqlService
service WidgetRepository {
    version: "1"
    operations: [CreateWidget, GetWidget, UpdateWidget, DeleteWidget]
}
