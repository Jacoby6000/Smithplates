package com.jacoby6000.smithplates.sql.service.renderer.codegentest

import com.jacoby6000.smithplates.codegen.SmithyNamespaceMapping

object SmithyNamespaceTestSupport {
  def parseSmithyNamespace(smithyContent: String): String =
    internal.NamespacePattern
      .findFirstMatchIn(smithyContent)
      .map(_.group(1))
      .getOrElse("example")

  def namespacePathPrefix(namespace: String): String =
    SmithyNamespaceMapping.pathPrefix(namespace)

  /** Internal implementation surface — not part of the stable API; subject to change without notice. */
  object internal {
    val NamespacePattern = """namespace\s+([^\s\n]+)""".r
  }
}
