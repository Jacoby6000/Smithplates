$version: "2.0"
namespace example

use stache.codegen.sql#sqlAutoUuid
use stache.codegen.sql#sqlCreatedTimestamp
use stache.codegen.sql#sqlPrimaryKey
use stache.codegen.sql#sqlTable
use stache.codegen.sql#sqlUpdatedTimestamp
use stache.codegen.sql#DerivedStruct
use stache.codegen.sql#sqlDeriveDelete
use stache.codegen.sql#sqlDeriveInsert
use stache.codegen.sql#sqlDeriveSelectOne
use stache.codegen.sql#sqlDeriveUpdate
use stache.codegen.sql#sqlService
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
    output: String
}

@sqlDeriveSelectOne(targetTable: "example#Widget")
operation GetWidget {
    input: DerivedStruct
    output: Widget
    errors: [WidgetNotFound]
}

@sqlDeriveUpdate(targetTable: "example#Widget")
operation UpdateWidget {
    input: DerivedStruct
    output: Boolean
}

@sqlDeriveDelete(targetTable: "example#Widget")
operation DeleteWidget {
    input: DerivedStruct
    output: Boolean
}

@sqlService
service WidgetRepository {
    version: "1"
    operations: [CreateWidget, GetWidget, UpdateWidget, DeleteWidget]
}
