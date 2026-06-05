package com.jacoby6000.smithy.mustachetest

/** Identifies a language/service-type/implementation variant for golden tests. */
final case class MustacheTemplateVariant(
    languageId: String,
    serviceTypeId: String,
    implementationId: String
) extends Ordered[MustacheTemplateVariant] {
  def srcOutputRootId: String = s"$languageId/src"

  def testOutputRootId: String = s"$languageId/test"

  def serviceTypeResourcePath: String = s"$srcOutputRootId/$serviceTypeId"

  def resourcePath: String = s"$serviceTypeResourcePath/$implementationId"

  def testOutputResourcePath: String = s"$testOutputRootId/$serviceTypeId/$implementationId"

  override def compare(that: MustacheTemplateVariant): Int =
    resourcePath.compare(that.resourcePath)
}
