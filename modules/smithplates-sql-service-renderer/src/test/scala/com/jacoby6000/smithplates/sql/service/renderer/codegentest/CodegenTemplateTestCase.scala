package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import java.nio.file.Path

final case class CodegenTemplateTestCase(
    name: String,
    caseDirectory: Path,
    smithyModelId: String,
    smithyContent: String,
    expectedOutputsByVariant: Map[CodegenTemplateVariant, List[CodegenTemplateExpectedFile]]
)

final case class CodegenTemplateExpectedFile(
    relativePath: String,
    content: String
)
