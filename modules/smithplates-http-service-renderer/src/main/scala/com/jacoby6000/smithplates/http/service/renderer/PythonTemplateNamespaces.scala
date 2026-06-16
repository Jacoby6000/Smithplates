package com.jacoby6000.smithplates.http.service.renderer

/** Classpath namespaces for bundled Python SSP templates. */
object PythonTemplateNamespaces {
  val Common: String     = "python/src/common"
  val Http: String       = "python/src/http"
  val HttpServer: String = s"$Http/server"
  val HttpClient: String = s"$Http/client"
  val HttpModels: String = s"$Http/models"

  val bundledHttpServerTemplateDirectory: String =
    s"classpath:$HttpServer"

  val bundledHttpClientTemplateDirectory: String =
    s"classpath:$HttpClient"

  val bundledHttpModelsTemplateDirectory: String =
    s"classpath:$HttpModels"

  def commonPreambleClasspath: String =
    s"$Common/fragments/preamble.ssp"

  def sharedHttpNamingPreambleClasspath: String =
    s"$Http/fragments/naming/preamble.ssp"

  def namingPreambleClasspath(templateRoot: String): String = {
    val normalized = templateRoot.stripPrefix("/").stripSuffix("/")
    s"$normalized/fragments/naming/preamble.ssp"
  }

  def isHttpCodegenTemplateRoot(normalizedTemplateRoot: String): Boolean =
    normalizedTemplateRoot == HttpServer || normalizedTemplateRoot == HttpClient || normalizedTemplateRoot == HttpModels
}
