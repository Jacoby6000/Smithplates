package com.jacoby6000.smithy.stache.mustachetest

final case class MustacheTemplateTestCase(
    name: String,
    resourceBasePath: String,
    smithyModelId: String,
    smithyContent: String,
    expectedOutputsByVariant: Map[MustacheTemplateVariant, List[MustacheTemplateExpectedFile]]
)

final case class MustacheTemplateExpectedFile(
    relativePath: String,
    content: String
)
