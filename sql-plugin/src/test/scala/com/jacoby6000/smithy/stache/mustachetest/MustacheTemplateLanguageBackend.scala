package com.jacoby6000.smithy.stache.mustachetest

import software.amazon.smithy.model.Model

trait MustacheTemplateLanguageBackend {
  def variant: MustacheTemplateVariant

  def loadModel(testCase: MustacheTemplateTestCase): Either[String, Model]

  def render(testCase: MustacheTemplateTestCase, model: Model): Either[String, Map[String, String]]

  def validateRenderedOutputs(
      testCase: MustacheTemplateTestCase,
      rendered: Map[String, String]
  ): Unit = {
    val _ = (testCase, rendered)
  }
}
