$version: "2.0"
namespace example

use smithplates.codegen.http#httpCookieAuth
use smithplates.codegen.http#httpService
use smithy.api#auth
use smithy.api#http
use smithy.api#httpApiKeyAuth
use smithy.api#httpBearerAuth
use smithy.api#httpHeader
use smithy.api#httpQuery
use smithy.api#optionalAuth
use smithy.api#tags

@httpService
@httpBearerAuth
@httpApiKeyAuth(name: "X-API-Key", in: "header", scheme: "ApiKey")
@httpCookieAuth(name: "session")
@auth([httpCookieAuth, httpBearerAuth, httpApiKeyAuth])
service AuthApi {
    version: "1"
    operations: [Required, Optional, Public, BearerOnly, ApiKeyOnly, CookieOnly]
}

@tags(["auth"])
@http(method: "GET", uri: "/required", code: 200)
operation Required {
    input: RequestInput
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

@auth([httpBearerAuth])
@tags(["auth"])
@http(method: "GET", uri: "/bearer", code: 200)
operation BearerOnly {
    output: AuthOutput
}

@auth([httpApiKeyAuth])
@tags(["auth"])
@http(method: "GET", uri: "/api-key", code: 200)
operation ApiKeyOnly {
    output: AuthOutput
}

@auth([httpCookieAuth])
@tags(["auth"])
@http(method: "GET", uri: "/cookie", code: 200)
operation CookieOnly {
    output: AuthOutput
}

structure RequestInput {
    @httpHeader("X-Trace")
    trace: String

    @httpQuery("filter")
    filter: String
}

structure AuthOutput {
    @required
    authenticated: Boolean
}

@httpService
@httpApiKeyAuth(name: "api_key", in: "query")
@auth([httpApiKeyAuth])
service QueryAuthApi {
    version: "1"
    operations: [QueryApiKey]
}

@tags(["query_auth"])
@http(method: "GET", uri: "/query-key", code: 200)
operation QueryApiKey {
    input: QueryInput
    output: AuthOutput
}

structure QueryInput {
    @httpQuery("filter")
    filter: String
}
