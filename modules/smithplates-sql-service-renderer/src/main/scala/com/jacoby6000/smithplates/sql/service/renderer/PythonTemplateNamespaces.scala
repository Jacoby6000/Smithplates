package com.jacoby6000.smithplates.sql.service.renderer

/** Classpath namespaces for bundled Python SSP templates. */
object PythonTemplateNamespaces {
  val Common: String = "python/src/common"
  val Db: String     = "python/src/db"

  val bundledDbTemplateDirectory: String =
    s"classpath:$Db"

  def commonPreambleClasspath: String =
    s"$Common/fragments/preamble.ssp"

  def namingPreambleClasspath(templateRoot: String): String = {
    val normalized = templateRoot.stripPrefix("/").stripSuffix("/")
    s"$normalized/fragments/naming/preamble.ssp"
  }
}
