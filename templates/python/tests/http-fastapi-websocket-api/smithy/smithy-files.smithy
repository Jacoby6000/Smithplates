$version: "2.0"

namespace example

use smithplates.codegen.http#httpService
use smithplates.codegen.http#websocket
use smithy.api#http
use smithy.api#tags
use smithy.api#readonly

@httpService
service ChatApi {
    version: "1"
    operations: [ListRooms, ChatStream]
}

@tags(["rooms"])
@http(method: "GET", uri: "/rooms", code: 200)
@readonly
operation ListRooms {
    output: RoomListOutput
}

@tags(["rooms"])
@http(method: "GET", uri: "/chat", code: 200)
@websocket
operation ChatStream {
    input: ClientMessage
    output: ServerMessage
}

structure Room {
    @required
    roomId: String

    @required
    name: String
}

list RoomList {
    member: Room
}

structure RoomListOutput {
    @required
    rooms: RoomList
}

union ClientMessage {
    join: JoinRoom
    leave: LeaveRoom
    ping: Ping
}

structure JoinRoom {
    @required
    roomId: String
}

structure LeaveRoom {
    @required
    roomId: String
}

structure Ping {
    @required
    nonce: String
}

union ServerMessage {
    welcome: Welcome
    roomJoined: RoomJoined
    pong: Pong
    error: ServerError
}

structure Welcome {
    @required
    message: String
}

structure RoomJoined {
    @required
    roomId: String
}

structure Pong {
    @required
    nonce: String
}

structure ServerError {
    @required
    detail: String
}
