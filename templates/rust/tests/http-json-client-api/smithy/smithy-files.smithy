$version: "2"
namespace example

use smithplates.codegen.http#httpService
use smithplates.codegen.http#httpProblem
use smithplates.codegen.http#httpCookieAuth
use smithplates.codegen.http#httpStaticHeader

@httpService
@httpBearerAuth
@httpApiKeyAuth(name: "X-API-Key", in: "header", scheme: "ApiKey")
@httpCookieAuth(name: "session")
@auth([httpBearerAuth, httpApiKeyAuth, httpCookieAuth])
service Catalog {
    version: "2026-10-09"
    operations: [GetItem, PutItem, DeleteItem, Secured, Optional, ApiKey, Cookie, PutNested, ReadHeaders, ReadHeaderOnly, PutOptionalNested, Transport]
    errors: [NotFound]
}

@http(method: "GET", uri: "/items/{id}", code: 200)
@auth([])
@tags(["items"])
operation GetItem { input: GetItemInput, output: Item }

@httpStaticHeader(name: "X-Contract", value: "catalog")
structure GetItemInput {
    @httpHeader("X-Request-Id") requestId: String
    @required @httpLabel id: String
    @httpQuery("filter") filters: StringList
}

@http(method: "PUT", uri: "/items/{id}", code: 200)
@auth([])
@tags(["items"])
operation PutItem { input: PutItemInput, output: Item, errors: [Conflict] }

structure PutItemInput {
    @required @httpLabel id: String
    @required @jsonName("wireName") displayName: Name
    @required state: State
    choice: Choice
    metadata: StringMap
    document: Document
    count: Integer
    active: Boolean
    sequence: Long
}

@http(method: "DELETE", uri: "/items/{id}", code: 204)
@auth([])
@tags(["items"])
operation DeleteItem { input: DeleteItemInput }

structure DeleteItemInput { @required @httpLabel id: String }
structure Item {
    @required id: String
    @required @jsonName("wireName") displayName: Name
    @required state: State
    @jsonName("type") type: String
    choice: Choice
}
string Name
list StringList { member: String }
map StringMap { key: String, value: String }
enum State {
    AVAILABLE = "available"
    UNAVAILABLE = "unavailable"
}
union Choice { @jsonName("wireText") text: String, number: Integer }

@error("client")
@httpError(404)
@httpProblem(type: "urn:problem:not-found", title: "Not found", code: 404)
structure NotFound {
    message: String
    @required @httpHeader("X-Reason") reason: Reason
}
string Reason

@error("client")
@httpError(409)
structure Conflict { @required message: String }

@http(method: "GET", uri: "/secured", code: 200)
@tags(["items"])
operation Secured { output: Item }

@optionalAuth
@http(method: "GET", uri: "/optional", code: 200)
@tags(["items"])
operation Optional { output: Item }

@auth([httpApiKeyAuth])
@http(method: "GET", uri: "/api-key", code: 200)
@tags(["items"])
operation ApiKey { output: Item }

@auth([httpCookieAuth])
@http(method: "GET", uri: "/cookie", code: 200)
@tags(["items"])
operation Cookie { output: Item }

@auth([])
@http(method: "PUT", uri: "/nested/{id}", code: 200)
@tags(["items"])
operation PutNested { input: NestedInput, output: Item }
structure NestedInput {
    @required @httpLabel id: String
    @required @httpPayload @nestedProperties body: Item
}

@auth([])
@http(method: "GET", uri: "/headers", code: 200)
@tags(["items"])
operation ReadHeaders { output: HeaderOutput }
@httpStaticHeader(name: "X-Contract", value: "catalog")
structure HeaderOutput {
    @required @httpHeader("X-Count") count: Count
    @httpHeader("X-Trace") trace: String
    @required value: String
}

@auth([])
@http(method: "GET", uri: "/headers-only", code: 200)
@tags(["items"])
operation ReadHeaderOnly { output: HeaderOnlyOutput }
structure HeaderOnlyOutput { @required @httpHeader("X-Count") count: Long }
integer Count

@auth([])
@http(method: "PUT", uri: "/optional-nested", code: 200)
@tags(["items"])
operation PutOptionalNested { input: OptionalNestedInput, output: Item }
structure OptionalNestedInput {
    @jsonName("payload") @httpPayload @nestedProperties body: Item
}

@auth([])
@http(method: "GET", uri: "/transport", code: 200)
@tags(["items"])
operation Transport { output: Item }
