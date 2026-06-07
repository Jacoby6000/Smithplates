package com.jacoby6000.smithy.stache.sql.codegen

import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateTestCase
import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateValidator
import com.jacoby6000.smithy.stache.codegentest.CodegenTemplateVariant

final case class PythonCodegenTemplateValidator(dialectKey: String) extends CodegenTemplateValidator {
  def validate(
      testCase: CodegenTemplateTestCase,
      variant: CodegenTemplateVariant,
      rendered: Map[String, String]
  ): Unit = {
    val artifacts =
      rendered.toList.map { case (relativePath, content) =>
        SqlCodegenArtifact(
          relativePath = relativePath,
          content = content,
          kind = if (relativePath.startsWith(s"${variant.testOutputRootId}/")) {
            SqlServiceCodegenArtifactKind.Test
          } else {
            SqlServiceCodegenArtifactKind.Src
          }
        )
      }
    PythonCodegenWorkspace.validateCase(
      testCase.name,
      artifacts,
      dialectKey
    )
  }
}
