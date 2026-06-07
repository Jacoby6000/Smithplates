package com.jacoby6000.smithy.stache.codegentest

trait CodegenTemplateValidator {
  def validate(
      testCase: CodegenTemplateTestCase,
      variant: CodegenTemplateVariant,
      rendered: Map[String, String]
  ): Unit
}
