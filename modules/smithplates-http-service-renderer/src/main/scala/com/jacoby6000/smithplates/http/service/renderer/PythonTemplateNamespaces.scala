package com.jacoby6000.smithplates.http.service.renderer

/** Classpath namespaces for bundled Python SSP templates. */
object PythonTemplateNamespaces {
  val Common: String = "python/src/common"
  val Http: String   = "python/src/http"

  val bundledHttpTemplateDirectory: String =
    s"classpath:$Http"

  def commonPreambleClasspath: String =
    s"$Common/fragments/preamble.ssp"

  def namingPreambleClasspath(templateRoot: String): String = {
    val normalized = templateRoot.stripPrefix("/").stripSuffix("/")
    s"$normalized/fragments/naming/preamble.ssp"
  }
}
