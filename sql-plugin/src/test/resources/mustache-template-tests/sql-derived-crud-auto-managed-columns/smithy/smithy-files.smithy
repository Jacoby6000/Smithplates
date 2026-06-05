$version: "2.0"
namespace example

use jacoby6000.codegen.sql#sqlAutoUuid
use jacoby6000.codegen.sql#sqlCreatedTimestamp
use jacoby6000.codegen.sql#sqlPrimaryKey
use jacoby6000.codegen.sql#sqlTable
use jacoby6000.codegen.sql#sqlUpdatedTimestamp
use jacoby6000.codegen.sql#DerivedStruct
use jacoby6000.codegen.sql#sqlDeriveDelete
use jacoby6000.codegen.sql#sqlDeriveInsert
use jacoby6000.codegen.sql#sqlDeriveSelectOne
use jacoby6000.codegen.sql#sqlDeriveUpdate
use jacoby6000.codegen.sql#sqlService
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
