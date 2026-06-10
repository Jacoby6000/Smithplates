package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import software.amazon.smithy.model.Model

trait CodegenTemplateBackend {
  def variant: CodegenTemplateVariant

  def loadModel(testCase: CodegenTemplateTestCase): Either[String, Model]

  def render(testCase: CodegenTemplateTestCase, model: Model): Either[String, Map[String, String]]
}
