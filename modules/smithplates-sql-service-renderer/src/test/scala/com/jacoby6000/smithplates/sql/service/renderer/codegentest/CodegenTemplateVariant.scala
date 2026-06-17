package com.jacoby6000.smithplates.sql.service.renderer.codegentest

/** Identifies a language/service-type/implementation variant for golden tests. */
final case class CodegenTemplateVariant(
    languageId: String,
    serviceTypeId: String,
    implementationId: String
) extends Ordered[CodegenTemplateVariant] {
  def srcOutputRootId: String = "src/generated"

  def testOutputRootId: String = "test"

  def serviceTypeResourcePath: String = s"$srcOutputRootId/$serviceTypeId"

  def resourcePath: String = s"$serviceTypeResourcePath/$implementationId"

  def testOutputResourcePath: String = s"$testOutputRootId/$serviceTypeId/$implementationId"

  def unsupportedFilePath: String = s"expected/$resourcePath/unsupported.md"

  def goldenTestLayout: GoldenTestLayout =
    serviceTypeId match {
      case "db"   => GoldenTestLayout.SqlDialect
      case "http" => GoldenTestLayout.HttpNested
      case other  =>
        throw new IllegalArgumentException(s"unsupported golden test serviceTypeId: $other")
    }

  def serviceTypeRootPrefix: String = s"$srcOutputRootId/$serviceTypeId/"

  def implementationSrcPrefix: String = s"$serviceTypeRootPrefix$implementationId/"

  def implementationTestPrefix: String = s"$testOutputRootId/$serviceTypeId/$implementationId/"

  def sharedModelsResourcePath: Option[String] =
    Some(s"$serviceTypeResourcePath/models")

  def matchesGeneratedOutputPath(outputRelativePath: String): Boolean = {
    val sharedModelPrefix =
      sharedModelsResourcePath.map(path => s"$path/").getOrElse("")

    if (sharedModelPrefix.nonEmpty && outputRelativePath.startsWith(sharedModelPrefix)) {
      true
    } else if (outputRelativePath.startsWith(implementationSrcPrefix)) {
      true
    } else if (outputRelativePath.startsWith(implementationTestPrefix)) {
      true
    } else if (outputRelativePath.startsWith(serviceTypeRootPrefix)) {
      goldenTestLayout match {
        case GoldenTestLayout.SqlDialect =>
          !outputRelativePath.startsWith(s"${serviceTypeRootPrefix}sqlite/") &&
          !outputRelativePath.startsWith(s"${serviceTypeRootPrefix}postgres/")
        case GoldenTestLayout.HttpNested =>
          outputRelativePath.startsWith(implementationSrcPrefix) ||
          outputRelativePath.startsWith(s"${serviceTypeRootPrefix}models/")
      }
    } else {
      false
    }
  }

  override def compare(that: CodegenTemplateVariant): Int =
    resourcePath.compare(that.resourcePath)
}

enum GoldenTestLayout {
  case SqlDialect
  case HttpNested
}
