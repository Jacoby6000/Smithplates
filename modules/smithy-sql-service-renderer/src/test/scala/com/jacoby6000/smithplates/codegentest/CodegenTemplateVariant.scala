package com.jacoby6000.smithplates.codegentest

/** Identifies a language/service-type/implementation variant for golden tests. */
final case class CodegenTemplateVariant(
    languageId: String,
    serviceTypeId: String,
    implementationId: String
) extends Ordered[CodegenTemplateVariant] {
  def srcOutputRootId: String = "src"

  def testOutputRootId: String = "test"

  def serviceTypeResourcePath: String = s"$srcOutputRootId/$serviceTypeId"

  def resourcePath: String = s"$serviceTypeResourcePath/$implementationId"

  def testOutputResourcePath: String = s"$testOutputRootId/$serviceTypeId/$implementationId"

  def unsupportedFilePath: String = s"expected/$resourcePath/unsupported.md"

  override def compare(that: CodegenTemplateVariant): Int =
    resourcePath.compare(that.resourcePath)
}
