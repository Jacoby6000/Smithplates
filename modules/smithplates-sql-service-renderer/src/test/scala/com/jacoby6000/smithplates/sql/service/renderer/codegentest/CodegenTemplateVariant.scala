package com.jacoby6000.smithplates.sql.service.renderer.codegentest

/** Identifies a language/service-type/implementation variant for golden tests. */
final case class CodegenTemplateVariant(
    languageId: String,
    serviceTypeId: String,
    implementationId: String
) extends Ordered[CodegenTemplateVariant] {
  def srcOutputRootId: String = "src/generated"

  def testOutputRootId: String = "test"

  def namespaceResourcePath(namespacePathPrefix: String): String =
    s"$srcOutputRootId/$namespacePathPrefix"

  def resourcePath(namespacePathPrefix: String): String =
    s"${namespaceResourcePath(namespacePathPrefix)}/$implementationId"

  def testOutputResourcePath(namespacePathPrefix: String): String =
    s"$testOutputRootId/$namespacePathPrefix/$implementationId"

  def unsupportedFilePath(namespacePathPrefix: String): String =
    s"expected/${resourcePath(namespacePathPrefix)}/unsupported.md"

  def goldenTestLayout: GoldenTestLayout =
    serviceTypeId match {
      case "db"   => GoldenTestLayout.SqlDialect
      case "http" => GoldenTestLayout.HttpNested
      case other  =>
        throw new IllegalArgumentException(s"unsupported golden test serviceTypeId: $other")
    }

  def namespaceRootPrefix(namespacePathPrefix: String): String =
    s"${namespaceResourcePath(namespacePathPrefix)}/"

  def implementationSrcPrefix(namespacePathPrefix: String): String =
    goldenTestLayout match {
      case GoldenTestLayout.SqlDialect =>
        s"${namespaceRootPrefix(namespacePathPrefix)}$implementationId/"
      case GoldenTestLayout.HttpNested =>
        namespaceRootPrefix(namespacePathPrefix)
    }

  def implementationTestPrefix(namespacePathPrefix: String): String =
    s"$testOutputRootId/$namespacePathPrefix/$implementationId/"

  def sharedModelsResourcePath(namespacePathPrefix: String): Option[String] =
    goldenTestLayout match {
      case GoldenTestLayout.SqlDialect =>
        Some(s"${namespaceResourcePath(namespacePathPrefix)}/models")
      case GoldenTestLayout.HttpNested =>
        None
    }

  private def isHttpClientPath(outputRelativePath: String): Boolean =
    outputRelativePath.contains("/client/") || outputRelativePath.contains("/clients/")

  private def isHttpServerPath(outputRelativePath: String, namespacePathPrefix: String): Boolean = {
    val namespaceRoot = namespaceRootPrefix(namespacePathPrefix)
    outputRelativePath.startsWith(namespaceRoot) &&
    !isHttpClientPath(outputRelativePath) &&
    (outputRelativePath.startsWith(s"${namespaceRoot}apis/") ||
      outputRelativePath.startsWith(s"${namespaceRoot}app_") ||
      outputRelativePath.startsWith(s"${namespaceRoot}api_") ||
      outputRelativePath == s"${namespaceRoot}operation_bindings.py")
  }

  private def isSqlProtocolOutputPath(outputRelativePath: String, namespacePathPrefix: String): Boolean = {
    val namespaceRoot = namespaceRootPrefix(namespacePathPrefix)
    if (!outputRelativePath.startsWith(namespaceRoot)) {
      false
    } else {
      val relativeToRoot = outputRelativePath.stripPrefix(namespaceRoot)
      relativeToRoot.endsWith("_protocol.py") && !relativeToRoot.contains("/")
    }
  }

  def matchesGeneratedOutputPath(outputRelativePath: String, namespacePathPrefix: String): Boolean = {
    val sharedModelPrefix =
      sharedModelsResourcePath(namespacePathPrefix).map(path => s"$path/").getOrElse("")

    if (sharedModelPrefix.nonEmpty && outputRelativePath.startsWith(sharedModelPrefix)) {
      true
    } else if (outputRelativePath.startsWith(implementationTestPrefix(namespacePathPrefix))) {
      true
    } else {
      goldenTestLayout match {
        case GoldenTestLayout.SqlDialect =>
          if (outputRelativePath.startsWith(implementationSrcPrefix(namespacePathPrefix))) {
            true
          } else if (isSqlProtocolOutputPath(outputRelativePath, namespacePathPrefix)) {
            true
          } else {
            false
          }
        case GoldenTestLayout.HttpNested =>
          if (implementationId == "server") {
            isHttpServerPath(outputRelativePath, namespacePathPrefix) ||
            (outputRelativePath.startsWith(namespaceRootPrefix(namespacePathPrefix)) &&
              outputRelativePath.endsWith(".py") &&
              !isHttpClientPath(outputRelativePath) &&
              !outputRelativePath.startsWith(s"${namespaceRootPrefix(namespacePathPrefix)}apis/") &&
              !outputRelativePath.startsWith(s"${namespaceRootPrefix(namespacePathPrefix)}app_") &&
              !outputRelativePath.startsWith(s"${namespaceRootPrefix(namespacePathPrefix)}api_") &&
              outputRelativePath != s"${namespaceRootPrefix(namespacePathPrefix)}operation_bindings.py")
          } else {
            isHttpClientPath(outputRelativePath) ||
            (outputRelativePath.startsWith(namespaceRootPrefix(namespacePathPrefix)) &&
              outputRelativePath.endsWith(".py") &&
              !isHttpServerPath(outputRelativePath, namespacePathPrefix))
          }
      }
    }
  }

  override def compare(that: CodegenTemplateVariant): Int =
    s"$languageId/$serviceTypeId/$implementationId".compare(
      that.languageId + "/" + that.serviceTypeId + "/" + that.implementationId)
}

enum GoldenTestLayout {
  case SqlDialect
  case HttpNested
}
