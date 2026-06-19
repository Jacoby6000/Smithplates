$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#http
use smithy.api#httpPayload
use smithy.api#tags

/// Golden case: unions referenced only as list members are emitted and validate.
@httpService
service EventApi {
    version: "1"
    operations: [ListEvents]
}

@tags(["events"])
@http(method: "GET", uri: "/events", code: 200)
operation ListEvents {
    input: Unit
    output: ListEvents200
}

structure ListEvents200 {
    @httpPayload
    @required
    body: EventList
}

structure EventList {
    @required
    items: EventListItems
}

list EventListItems {
    member: Event
}

union Event {
    created: EventCreated
    deleted: EventDeleted
}

structure EventCreated {
    @required
    eventId: String
}

structure EventDeleted {
    @required
    eventId: String
}
