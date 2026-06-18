package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import com.jacoby6000.smithplates.codegen.SmithyNamespaceMapping

object SmithyNamespaceTestSupport {
  private val NamespacePattern = """namespace\s+([^\s\n]+)""".r

  def parseSmithyNamespace(smithyContent: String): String =
    NamespacePattern
      .findFirstMatchIn(smithyContent)
      .map(_.group(1))
      .getOrElse("example")

  def namespacePathPrefix(namespace: String): String =
    SmithyNamespaceMapping.pathPrefix(namespace)
}
