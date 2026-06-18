package com.jacoby6000.smithplates.codegen

/** Converts Smithy shape namespaces to filesystem path segments and Python package segments. */
object SmithyNamespaceMapping {
  def pathSegments(namespace: String): List[String] =
    namespace.split('.').toList.filter(_.nonEmpty)

  def pathPrefix(namespace: String): String =
    pathSegments(namespace).mkString("/")
}
