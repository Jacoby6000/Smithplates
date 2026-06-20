$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#enum
use smithy.api#http
use smithy.api#httpLabel
use smithy.api#httpQuery
use smithy.api#pattern
use smithy.api#tags

/// Golden case: string aliases render as str and enums are imported in service protocols.
@httpService
service ItemApi {
    version: "1"
    operations: [GetItem, ListItems]
}

@pattern("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
string ItemId

enum ItemKind {
    FILE
    FOLDER
}

@tags(["items"])
@http(method: "GET", uri: "/items/{itemId}", code: 200)
operation GetItem {
    input: GetItemInput
    output: GetItem200
}

@tags(["items"])
@http(method: "GET", uri: "/items", code: 200)
operation ListItems {
    input: ListItemsInput
    output: ListItems200
}

structure GetItemInput {
    @required
    @httpLabel
    itemId: ItemId
}

structure GetItem200 {
    @httpPayload
    @required
    body: ItemOutput
}

structure ListItemsInput {
    @httpQuery("kind")
    kind: ItemKind
}

structure ListItems200 {
    @httpPayload
    @required
    body: ItemListOutput
}

structure ItemOutput {
    @required
    itemId: ItemId
}

structure ItemListOutput {
    @required
    items: String
}
