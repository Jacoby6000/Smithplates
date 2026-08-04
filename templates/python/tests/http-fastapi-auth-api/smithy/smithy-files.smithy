$version: "2.0"
namespace example

use smithplates.codegen.http#httpCookieAuth
use smithplates.codegen.http#httpService
use smithy.api#auth
use smithy.api#http
use smithy.api#httpApiKeyAuth
use smithy.api#httpBearerAuth
use smithy.api#optionalAuth
use smithy.api#tags

/// Authentication fixture service.
@httpService
@httpBearerAuth
@httpApiKeyAuth(name: "X-API-Key", in: "header", scheme: "ApiKey")
@httpCookieAuth(name: "session")
@auth([httpBearerAuth, httpApiKeyAuth, httpCookieAuth])
service AuthApi {
    version: "1"
    operations: [Required, Optional, Public]
}

@tags(["auth"])
@http(method: "GET", uri: "/required", code: 200)
operation Required {
    output: AuthOutput
}

@optionalAuth
@tags(["auth"])
@http(method: "GET", uri: "/optional", code: 200)
operation Optional {
    output: AuthOutput
}

@auth([])
@tags(["auth"])
@http(method: "GET", uri: "/public", code: 200)
operation Public {
    output: AuthOutput
}

structure AuthOutput {
    @required
    authenticated: Boolean
}
