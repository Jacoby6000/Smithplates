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

  def matchesGeneratedOutputPath(outputRelativePath: String, namespacePathPrefix: String): Boolean = {
    val sharedModelPrefix =
      sharedModelsResourcePath(namespacePathPrefix).map(path => s"$path/").getOrElse("")
    val namespaceRoot     = namespaceRootPrefix(namespacePathPrefix)

    if (sharedModelPrefix.nonEmpty && outputRelativePath.startsWith(sharedModelPrefix)) {
      true
    } else if (outputRelativePath.startsWith(implementationTestPrefix(namespacePathPrefix))) {
      true
    } else {
      goldenTestLayout match {
        case GoldenTestLayout.SqlDialect =>
          if (outputRelativePath.startsWith(implementationSrcPrefix(namespacePathPrefix))) {
            true
          } else if (CodegenTemplateVariant.internal.isSqlProtocolOutputPath(outputRelativePath, namespaceRoot)) {
            true
          } else if (CodegenTemplateVariant.internal.isSqlNamespaceRootEnumPath(outputRelativePath, namespaceRoot)) {
            true
          } else {
            false
          }
        case GoldenTestLayout.HttpNested =>
          if (implementationId == "server") {
            CodegenTemplateVariant.internal.isHttpServerPath(outputRelativePath, namespaceRoot) ||
            (outputRelativePath.startsWith(namespaceRoot) &&
              outputRelativePath.endsWith(".py") &&
              !CodegenTemplateVariant.internal.isHttpClientPath(outputRelativePath) &&
              !outputRelativePath.startsWith(s"${namespaceRoot}apis/") &&
              !outputRelativePath.startsWith(s"${namespaceRoot}app_") &&
              !outputRelativePath.startsWith(s"${namespaceRoot}api_") &&
              outputRelativePath != s"${namespaceRoot}operation_bindings.py")
          } else {
            CodegenTemplateVariant.internal.isHttpClientPath(outputRelativePath) ||
            (outputRelativePath.startsWith(namespaceRoot) &&
              outputRelativePath.endsWith(".py") &&
              !CodegenTemplateVariant.internal.isHttpServerPath(outputRelativePath, namespaceRoot))
          }
      }
    }
  }

  override def compare(that: CodegenTemplateVariant): Int =
    s"$languageId/$serviceTypeId/$implementationId".compare(
      that.languageId + "/" + that.serviceTypeId + "/" + that.implementationId)
}

object CodegenTemplateVariant {

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    def isHttpClientPath(outputRelativePath: String): Boolean =
      outputRelativePath.contains("/client/") || outputRelativePath.contains("/clients/")

    def isHttpServerPath(outputRelativePath: String, namespaceRoot: String): Boolean =
      outputRelativePath.startsWith(namespaceRoot) &&
        !isHttpClientPath(outputRelativePath) &&
        (outputRelativePath.startsWith(s"${namespaceRoot}apis/") ||
          outputRelativePath.startsWith(s"${namespaceRoot}app_") ||
          outputRelativePath.startsWith(s"${namespaceRoot}api_") ||
          outputRelativePath == s"${namespaceRoot}operation_bindings.py")

    def isSqlProtocolOutputPath(outputRelativePath: String, namespaceRoot: String): Boolean =
      if (!outputRelativePath.startsWith(namespaceRoot)) {
        false
      } else {
        val relativeToRoot = outputRelativePath.stripPrefix(namespaceRoot)
        relativeToRoot.endsWith("_protocol.py") && !relativeToRoot.contains("/")
      }

    def isSqlNamespaceRootEnumPath(outputRelativePath: String, namespaceRoot: String): Boolean =
      if (!outputRelativePath.startsWith(namespaceRoot)) {
        false
      } else {
        val relativeToRoot = outputRelativePath.stripPrefix(namespaceRoot)
        relativeToRoot.endsWith(".py") &&
        !relativeToRoot.contains("/") &&
        !relativeToRoot.endsWith("_protocol.py")
      }
  }
}

enum GoldenTestLayout {
  case SqlDialect
  case HttpNested
}
