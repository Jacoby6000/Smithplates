$version: "2.0"
namespace example

use smithplates.codegen.http#httpService
use smithy.api#http
use smithy.api#tags
use smithy.api#readonly

@httpService
service ContentApi {
    version: "1"
    operations: [CreateContent, GetContent]
}

@tags(["content"])
@http(method: "POST", uri: "/content", code: 201)
operation CreateContent {
    input: CreateContentInput
    output: ContentOutput
}

@tags(["content"])
@http(method: "GET", uri: "/content/{contentId}", code: 200)
@readonly
operation GetContent {
    input: GetContentInput
    output: ContentOutput
}

structure PostalAddress {
    @required
    street: String

    @required
    city: String
}

structure ImageAsset {
    @required
    url: String

    @required
    width: Integer
}

union MediaAttachment {
    caption: String
    image: ImageAsset
    archivedAt: Timestamp
}

structure CreateContentInput {
    @required
    title: String

    @required
    authorAddress: PostalAddress

    @required
    attachment: MediaAttachment
}

structure GetContentInput {
    @required
    @httpLabel
    contentId: String
}

structure ContentOutput {
    @required
    contentId: String

    @required
    title: String

    @required
    authorAddress: PostalAddress

    @required
    attachment: MediaAttachment
}
