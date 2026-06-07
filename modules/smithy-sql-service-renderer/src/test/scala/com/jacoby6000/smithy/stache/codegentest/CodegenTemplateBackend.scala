package com.jacoby6000.smithy.stache.codegentest

import software.amazon.smithy.model.Model

trait CodegenTemplateBackend {
  def variant: CodegenTemplateVariant

  def loadModel(testCase: CodegenTemplateTestCase): Either[String, Model]

  def render(testCase: CodegenTemplateTestCase, model: Model): Either[String, Map[String, String]]

  def validateRenderedOutputs(
      testCase: CodegenTemplateTestCase,
      rendered: Map[String, String]
  ): Unit = {
    val _ = (testCase, rendered)
  }
}
