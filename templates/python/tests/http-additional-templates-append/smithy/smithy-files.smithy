$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#http
use smithy.api#readonly
use smithy.api#tags

@httpService
service PingApi {
    version: "1"
    operations: [Ping]
}

@tags(["ping"])
@http(method: "GET", uri: "/ping", code: 200)
@readonly
operation Ping {
    output: PingOutput
}

structure PingOutput {
    @required
    message: String
}
