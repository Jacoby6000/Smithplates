$version: "2.0"

namespace example

use smithplates.codegen.http#httpService
use smithplates.codegen.http#websocket
use smithy.api#http
use smithy.api#httpLabel
use smithy.api#readonly
use smithy.api#required
use smithy.api#tags

@httpService
service StreamApi {
    version: "1"
    operations: [ListStreams, StreamEvents]
}

@tags(["streams"])
@http(method: "GET", uri: "/streams", code: 200)
@readonly
operation ListStreams {
    output: StreamListOutput
}

structure Stream {
    @required
    streamId: String

    @required
    name: String
}

list StreamList {
    member: Stream
}

structure StreamListOutput {
    @required
    streams: StreamList
}

/// Push-only WebSocket endpoint delivering server events for a given stream.
/// Path params identify the stream; the server pushes events without receiving
/// any client-to-server messages.
@tags(["streams"])
@suppress(["HttpMethodSemantics"])
@http(method: "GET", uri: "/streams/{streamId}/events/ws", code: 200)
@readonly
@websocket
operation StreamEvents {
    input: StreamEventsInput
    output: ServerEvent
}

structure StreamEventsInput {
    @httpLabel
    @required
    streamId: String
}

union ServerEvent {
    started: StreamStarted
    progress: StreamProgress
    completed: StreamCompleted
}

structure StreamStarted {
    @required
    stream_id: String

    @required
    message: String
}

structure StreamProgress {
    @required
    stream_id: String

    @required
    percent: Integer
}

structure StreamCompleted {
    @required
    stream_id: String

    @required
    exit_code: Integer
}
