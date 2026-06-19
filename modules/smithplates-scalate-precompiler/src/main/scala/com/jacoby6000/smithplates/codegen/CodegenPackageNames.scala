package com.jacoby6000.smithplates.codegen

/** Derives namespace-aware Python packages and output path prefixes for generated artifacts. */
object CodegenPackageNames {
  def packageName(rootNamespace: Option[String], smithyNamespace: String): String = {
    val segments =
      rootNamespace.toList.flatMap(_.split('.').toList).filter(_.nonEmpty) ++
        SmithyNamespaceMapping.pathSegments(smithyNamespace)
    segments.mkString(".")
  }

  def outputPathPrefix(smithyNamespace: String): String =
    SmithyNamespaceMapping.pathPrefix(smithyNamespace)

  def resolvePackageName(
      rootNamespace: Option[String],
      smithyNamespace: String,
      packageNameOverride: Option[String]
  ): String =
    packageNameOverride.getOrElse(packageName(rootNamespace, smithyNamespace))
}
