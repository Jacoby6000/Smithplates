package com.jacoby6000.smithplates.codegentest

final case class CodegenTemplateTestCase(
    name: String,
    resourceBasePath: String,
    smithyModelId: String,
    smithyContent: String,
    expectedOutputsByVariant: Map[CodegenTemplateVariant, List[CodegenTemplateExpectedFile]]
)

final case class CodegenTemplateExpectedFile(
    relativePath: String,
    content: String
)
