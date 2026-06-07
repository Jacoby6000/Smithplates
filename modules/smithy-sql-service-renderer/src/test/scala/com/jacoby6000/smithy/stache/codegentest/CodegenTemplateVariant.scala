package com.jacoby6000.smithy.stache.codegentest

/** Identifies a language/service-type/implementation variant for golden tests. */
final case class CodegenTemplateVariant(
    languageId: String,
    serviceTypeId: String,
    implementationId: String
) extends Ordered[CodegenTemplateVariant] {
  def srcOutputRootId: String = s"$languageId/src"

  def testOutputRootId: String = s"$languageId/test"

  def serviceTypeResourcePath: String = s"$srcOutputRootId/$serviceTypeId"

  def resourcePath: String = s"$serviceTypeResourcePath/$implementationId"

  def testOutputResourcePath: String = s"$testOutputRootId/$serviceTypeId/$implementationId"

  override def compare(that: CodegenTemplateVariant): Int =
    resourcePath.compare(that.resourcePath)
}
